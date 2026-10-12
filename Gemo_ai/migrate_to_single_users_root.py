#!/usr/bin/env python3
"""
migrate_to_single_users_root.py
Safely backs up and standardizes Firebase Realtime Database to have ONLY ONE root node: /users/
"""

import urllib.request
import urllib.error
import json
import time
import os

FIREBASE_URL = "https://ussr-error-404-default-rtdb.firebaseio.com"

def http_req(method, path, data=None):
    url = f"{FIREBASE_URL}/{path.strip('/')}.json"
    req_data = None
    headers = {"Content-Type": "application/json"}
    if data is not None:
        req_data = json.dumps(data).encode("utf-8")
    req = urllib.request.Request(url, data=req_data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=15) as resp:
        body = resp.read().decode("utf-8")
        if body:
            return json.loads(body)
        return True

def run_migration():
    print("1. Downloading full database backup...")
    full_data = http_req("GET", "")
    backup_file = f"firebase_backup_{int(time.time())}.json"
    with open(backup_file, "w", encoding="utf-8") as f:
        json.dump(full_data, f, indent=2)
    print(f"   Backup successfully saved to {backup_file} ({len(json.dumps(full_data))} bytes)")

    # 2. Extract auth accounts
    auth_users = full_data.get("auth", {}).get("users", {}) if isinstance(full_data.get("auth"), dict) else {}
    auth_sessions = full_data.get("auth", {}).get("sessions", {}) if isinstance(full_data.get("auth"), dict) else {}

    print(f"2. Found {len(auth_users)} auth accounts: {list(auth_users.keys())}")

    # 3. For each account, ensure /users/{canonicalUsername}/ has complete profile and auth data
    for uname, acc in auth_users.items():
        canonical = uname.lower().strip()
        print(f"   Processing account: {canonical}")
        
        # User profile
        profile = {
            "username": acc.get("username", canonical),
            "canonicalUsername": canonical,
            "createdAt": acc.get("createdAt", int(time.time() * 1000))
        }
        http_req("PUT", f"users/{canonical}/profile", profile)

        # User auth metadata & sessions
        sessions_map = {}
        if isinstance(auth_sessions, dict):
            for s_hash, s_data in auth_sessions.items():
                if isinstance(s_data, dict):
                    if s_data.get("username", "").lower() == canonical or s_data.get("userId", "").lower() == canonical:
                        sessions_map[s_hash] = {
                            "sessionTokenHash": s_hash,
                            "createdAt": s_data.get("createdAt", int(time.time() * 1000)),
                            "expiresAt": s_data.get("expiresAt", int(time.time() * 1000) + 30*86400*1000),
                            "revoked": s_data.get("revoked", False)
                        }

        auth_node = {
            "passwordHash": acc.get("passwordHash", ""),
            "accountStatus": acc.get("accountStatus", "active"),
            "createdAt": acc.get("createdAt", int(time.time() * 1000)),
            "sessions": sessions_map
        }
        http_req("PUT", f"users/{canonical}/auth", auth_node)

        # Stop flag
        http_req("PUT", f"users/{canonical}/stop", {
            "stop": False,
            "conversationId": "",
            "requestId": "",
            "timestamp": int(time.time() * 1000)
        })
        http_req("PUT", f"users/{canonical}/stopGeneration", False)

    # 4. Migrate conversations from old random user IDs and root /conversations
    users_node = full_data.get("users", {}) if isinstance(full_data.get("users"), dict) else {}
    for old_uid, u_data in users_node.items():
        if isinstance(u_data, dict) and old_uid.startswith("user_"):
            target_user = "ussr_error_404" # Map to the registered owner
            convs = u_data.get("conversations", {})
            if isinstance(convs, dict) and convs:
                print(f"   Migrating {len(convs)} conversations from {old_uid} to users/{target_user}/conversations")
                for c_id, c_data in convs.items():
                    http_req("PUT", f"users/{target_user}/conversations/{c_id}", c_data)

    root_convs = full_data.get("conversations", {}) if isinstance(full_data.get("conversations"), dict) else {}
    if isinstance(root_convs, dict):
        for c_id, c_data in root_convs.items():
            if isinstance(c_data, dict):
                target_user = c_data.get("userId", "ussr_error_404")
                if target_user.startswith("user_") or target_user == "legacy_guest":
                    target_user = "ussr_error_404"
                print(f"   Syncing root conversation {c_id} to users/{target_user}/conversations")
                http_req("PUT", f"users/{target_user}/conversations/{c_id}", c_data)

    # 5. Clean up ephemeral user_... keys from /users/
    for old_uid in list(users_node.keys()):
        if old_uid.startswith("user_"):
            print(f"   Removing legacy node: users/{old_uid}")
            http_req("DELETE", f"users/{old_uid}")

    # 6. Delete all global root nodes that violate the single root requirement
    global_nodes_to_remove = [
        "auth", "agent", "model", "current_model", "selected_model",
        "online", "stop", "stopGeneration", "conversations",
        "workspace", "settings", "memory", "logolog"
    ]
    for node in global_nodes_to_remove:
        print(f"   Removing global root node: /{node}")
        http_req("DELETE", node)

    # 7. Final verification of shallow root keys
    shallow_root = http_req("GET", ".json?shallow=true")
    print(f"\nFinal Firebase Root Keys: {shallow_root}")
    assert list(shallow_root.keys()) == ["users"], f"Expected ONLY ['users'] but got {list(shallow_root.keys())}"
    print("MIGRATION COMPLETED SUCCESSFULLY! Database now has strictly ONE root node: /users/")

if __name__ == "__main__":
    run_migration()
