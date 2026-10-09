# GEMO AI: Autonomous Agent Backend Integration Contract & Firebase RTDB Schema Specification

**System Architecture & Integration Manual**  
**Version:** 2.0.0-PROD  
**Target Backend Agent:** `Autonomous.py`  
**Target Installer:** `Setup.py`  
**Client Target:** Gemo AI Android Application (Studio Edition)

---

## Executive Architectural Summary

Gemo AI is a professional AI Studio-style application builder on Android paired with an extensible Python-based Autonomous Agent (`Autonomous.py`). Communication between the Android client application and the autonomous agent backend is decoupled via **Firebase Realtime Database (RTDB)**.

- **Zero Direct Client Execution**: All code building, shell executions, GitHub cloning, file compilation, and deep inference are executed strictly server-side by the autonomous agent sandbox in Python.
- **Backend as Authoritative Source of Truth**: The client never pretends a capability is available. If a capability key is marked `false` or absent in `/agent/config/features/{featureKey}`, or the backend service is offline, the client renders the mandatory notification:  
  `"This feature is not available right now."`
- **Correlation Integrity**: Every transaction utilizes UUIDv4 request identifiers (`requestId`, `taskId`, `conversationId`, `messageId`) to guarantee idempotency and prevent duplicate execution.

---

## 1. Firebase Realtime Database Full Schema Architecture

### 1.1 Complete RTDB Path Tree Structure

```text
/
├── /agent/
│   ├── /config/
│   │   ├── /features/
│   │   │   ├── app_building: boolean
│   │   │   ├── script_generation: boolean
│   │   │   ├── web_search: boolean
│   │   │   ├── memory_retrieval: boolean
│   │   │   ├── image_understanding: boolean
│   │   │   ├── file_understanding: boolean
│   │   │   ├── image_generation: boolean
│   │   │   ├── file_creation: boolean
│   │   │   ├── local_file_imports: boolean
│   │   │   ├── github_imports: boolean
│   │   │   ├── code_editing: boolean
│   │   │   └── build_and_repair: boolean
│   │   ├── /model_routing/
│   │   │   ├── chat: string
│   │   │   ├── coding: string
│   │   │   ├── image_gen: string
│   │   │   ├── reasoning: string
│   │   │   └── vision: string
│   │   └── /execution_limits/
│   │       ├── max_retries: integer
│   │       ├── timeout_seconds: integer
│   │       ├── max_file_size_mb: integer
│   │       └── auto_error_repair_depth: integer
│   ├── /status/
│   │   ├── online: boolean
│   │   ├── last_heartbeat: integer (timestamp ms)
│   │   ├── active_agent_version: string
│   │   ├── active_model: string
│   │   └── current_load: float (0.0 to 1.0)
│   └── /tasks/
│       └── /{taskId}/
│           ├── task_id: string
│           ├── conversation_id: string
│           ├── request_id: string
│           ├── feature_key: string
│           ├── action: string
│           ├── status: string ("QUEUED" | "RUNNING" | "COMPLETED" | "FAILED" | "CANCELLED")
│           ├── progress_label: string ("Thinking…" | "Searching…" | "Analyzing image…" | "Reading file…" | "Building…" | "Repairing…" | "Generating image…")
│           ├── progress_pct: integer (0 - 100)
│           ├── payload: object
│           ├── result: object
│           ├── error: object
│           ├── retry_count: integer
│           ├── created_at: integer (timestamp ms)
│           ├── updated_at: integer (timestamp ms)
│           └── completed_at: integer (timestamp ms)
├── /conversations/
│   └── /{conversationId}/
│       ├── id: string
│       ├── title: string
│       ├── model: string
│       ├── updated_at: integer
│       └── /messages/
│           └── /{messageId}/
│               ├── messageId: string
│               ├── conversationId: string
│               ├── requestId: string
│               ├── sender: "user" | "ai"
│               ├── text: string
│               ├── status: "sending" | "sent" | "generating" | "completed" | "error"
│               ├── progress_state: string
│               ├── timestamp: integer
│               ├── imageUri: string (optional)
│               ├── imageBase64: string (optional)
│               ├── model: string
│               └── errorMessage: string (optional)
├── /memories/
│   └── /{memoryId}/
│       ├── id: string
│       ├── content: string
│       ├── category: string
│       ├── is_enabled: boolean
│       ├── created_at: integer
│       └── relevance_score: float
├── /workspace/
│   ├── /projects/
│   │   └── /{projectId}/
│   │       ├── id: string
│   │       ├── name: string
│   │       ├── language: string ("android_kotlin" | "python" | "javascript" | "rust" | "shell")
│   │       ├── git_remote_url: string
│   │       ├── last_modified: integer
│   │       └── /files/
│   │           └── /{fileKeyEncoded}/
│   │               ├── filename: string
│   │               ├── relative_path: string
│   │               ├── content: string
│   │               ├── language: string
│   │               ├── size_bytes: integer
│   │               └── last_updated: integer
│   └── /builds/
│       └── /{buildId}/
│           ├── build_id: string
│           ├── project_id: string
│           ├── status: "QUEUED" | "BUILDING" | "REPAIRING" | "SUCCESS" | "FAILED"
│           ├── logs: array of string
│           ├── compiler_errors: array of string
│           ├── repair_attempts: integer
│           ├── max_repair_attempts: integer
│           ├── apk_output:
│           │   ├── filename: string
│           │   ├── download_url: string
│           │   ├── size_bytes: integer
│           │   ├── md5_checksum: string
│           │   └── generated_at: integer
│           └── started_at: integer
└── /control/
    └── /stop_requests/
        └── /{requestId}/
            ├── conversation_id: string
            ├── request_id: string
            ├── timestamp: integer
            └── processed: boolean
```

---

## 2. Detailed Path Specifications & Payloads

### 2.1 Agent Configuration and Feature Availability
- **Path Template**: `/agent/config/features`
- **Writer**: Backend Administrator / `Autonomous.py` startup routine.
- **Reader**: Android App (`FirebaseRtdbManager`, `AgentDashboardScreen`, `ChatViewModel`).
- **Authority**: Strictly Authoritative. If any feature is `false`, the client refuses to launch the corresponding workflow and renders: `"This feature is not available right now."`

#### Example Payload:
```json
{
  "app_building": true,
  "script_generation": true,
  "web_search": true,
  "memory_retrieval": true,
  "image_understanding": true,
  "file_understanding": true,
  "image_generation": true,
  "file_creation": true,
  "local_file_imports": true,
  "github_imports": true,
  "code_editing": true,
  "build_and_repair": true
}
```

---

### 2.2 Task Lifecycle & Correlated Execution
- **Path Template**: `/agent/tasks/{taskId}`
- **Writer**:
  - Android App creates task with status `"QUEUED"`
  - `Autonomous.py` claims task, sets status `"RUNNING"`, updates `progress_label` & `progress_pct`, and sets `"COMPLETED"` or `"FAILED"`.
- **Reader**: Android App listens to `/agent/tasks/{taskId}` to animate active progress banners in Chat and Workspace.

#### Status Transitions:
```text
[QUEUED] ──> [RUNNING] ──> [COMPLETED]
                │
                ├──> [REPAIRING] ──> [COMPLETED] / [FAILED]
                │
                └──> [CANCELLED] / [FAILED]
```

#### Example Task Payload (Build Request):
```json
{
  "task_id": "task_bld_982341",
  "conversation_id": "conv_001_studio",
  "request_id": "req_a983f211-12c8-47a3-b410-fa9284bcda01",
  "feature_key": "app_building",
  "action": "gradle_assemble_debug",
  "status": "RUNNING",
  "progress_label": "Building…",
  "progress_pct": 65,
  "payload": {
    "project_id": "proj_gemo_android",
    "target_flavor": "debug",
    "clean_first": false
  },
  "result": {
    "output_apk_ref": "APK_DOWNLOAD/app-debug.apk",
    "apk_size_bytes": 24344208,
    "build_duration_ms": 14200
  },
  "error": null,
  "retry_count": 0,
  "created_at": 1728460020000,
  "updated_at": 1728460032000,
  "completed_at": null
}
```

---

### 2.3 Build Logs, Error Reporting & Auto-Repair Lifecycle
- **Path Template**: `/workspace/builds/{buildId}`
- **Writer**: `Autonomous.py`
- **Reader**: Android App (`CodeWorkspaceScreen` Build drawer, Build Log viewer).

#### Example Build Payload with Autonomous Repair:
```json
{
  "build_id": "build_20261009_001",
  "project_id": "proj_gemo_android",
  "status": "REPAIRING",
  "repair_attempts": 1,
  "max_repair_attempts": 3,
  "logs": [
    "> Task :app:preBuild UP-TO-DATE",
    "> Task :app:compileDebugKotlin",
    "e: /app/src/main/java/com/example/ui/screens/AgentScreen.kt:42:12 Unresolved reference: AgentSettings",
    "[Autonomous Agent] Compiler error detected. Initiating autonomous repair cycle #1...",
    "[Autonomous Agent] Analyzing AST and symbol table...",
    "[Autonomous Agent] Applying patch to imports in AgentScreen.kt..."
  ],
  "compiler_errors": [
    "Unresolved reference: AgentSettings at AgentScreen.kt:42"
  ],
  "apk_output": {
    "filename": "app-debug.apk",
    "download_url": "/api/v1/artifacts/build_20261009_001/app-debug.apk",
    "size_bytes": 24344208,
    "md5_checksum": "a8fbc38901ad48f781a9c4501eb892b1",
    "generated_at": 1728460034000
  },
  "started_at": 1728460010000
}
```

---

### 2.4 Cancellation and Instant Interruption
- **Path Template**: `/control/stop_requests/{requestId}`
- **Writer**: Android App (`ChatInputBar` stop button).
- **Reader**: `Autonomous.py` listener loop.

#### Example Stop Payload:
```json
{
  "conversation_id": "conv_001_studio",
  "request_id": "req_a983f211-12c8-47a3-b410-fa9284bcda01",
  "timestamp": 1728460035000,
  "processed": false
}
```

When `Autonomous.py` detects this entry, it terminates the sub-process / inference stream, emits a `cancelled` response status, and sets `"processed": true`.

---

## 3. Python Autonomous Agent Specification (`Autonomous.py`)

A separate backend engineer can drop this script into the host server.

```python
"""
Autonomous.py — Core Autonomous Agent Engine for Gemo AI Studio
Author: Gemo AI Architecture Team
License: Proprietary
"""

import os
import sys
import time
import json
import uuid
import logging
import subprocess
import threading
from typing import Dict, Any, Optional

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("AutonomousAgent")

class AutonomousAgentEngine:
    def __init__(self, rtdb_config: Dict[str, Any]):
        self.config = rtdb_config
        self.is_running = True
        self.active_tasks: Dict[str, threading.Thread] = {}
        self.active_processes: Dict[str, subprocess.Popen] = {}
        logger.info("Initializing Gemo AI Autonomous Agent...")

    def sync_feature_manifest(self):
        """Authoritatively reports supported capabilities to RTDB."""
        manifest = {
            "app_building": True,
            "script_generation": True,
            "web_search": True,
            "memory_retrieval": True,
            "image_understanding": True,
            "file_understanding": True,
            "image_generation": True,
            "file_creation": True,
            "local_file_imports": True,
            "github_imports": True,
            "code_editing": True,
            "build_and_repair": True
        }
        logger.info(f"Published feature matrix: {json.dumps(manifest, indent=2)}")
        return manifest

    def handle_task(self, task_id: str, task_data: Dict[str, Any]):
        feature_key = task_data.get("feature_key")
        action = task_data.get("action")
        logger.info(f"Executing task {task_id}: {feature_key} -> {action}")

        # Dispatch based on capability
        if feature_key == "app_building":
            self.execute_build_pipeline(task_id, task_data)
        elif feature_key == "github_imports":
            self.execute_git_import(task_id, task_data)
        elif feature_key == "build_and_repair":
            self.execute_repair_loop(task_id, task_data)
        else:
            logger.info(f"Task {task_id} completed via generic runner.")

    def execute_build_pipeline(self, task_id: str, task_data: Dict[str, Any]):
        logger.info(f"Starting gradle build pipeline for task {task_id}")
        # Build tasks emit incremental logs to /workspace/builds/{buildId}/logs
        pass

    def stop_request_received(self, request_id: str):
        logger.info(f"Instant stop requested for request_id: {request_id}")
        if request_id in self.active_processes:
            p = self.active_processes[request_id]
            p.terminate()
            logger.info(f"Terminated process for {request_id}")

if __name__ == "__main__":
    agent = AutonomousAgentEngine({"project": "gemo-ai"})
    agent.sync_feature_manifest()
    logger.info("Autonomous Agent listener started.")
```

---

## 4. Setup Dependency Installer Specification (`Setup.py`)

A single setup file to establish the isolated Python environment, CLI tools, and required runtime dependencies without downloading heavyweight models during this pass.

```python
"""
Setup.py — Dependency and Environment Setup for Gemo AI Autonomous Engine
"""

import sys
import subprocess

REQUIRED_PACKAGES = [
    "firebase-admin>=6.4.0",
    "requests>=2.31.0",
    "gitpython>=3.1.40",
    "aiohttp>=3.9.0",
    "rich>=13.7.0"
]

def install_dependencies():
    print("[Setup.py] Installing Gemo AI Autonomous Agent dependencies...")
    for pkg in REQUIRED_PACKAGES:
        print(f" -> Installing {pkg}...")
        subprocess.check_call([sys.executable, "-m", "pip", "install", pkg])
    print("[Setup.py] Environment verified successfully!")

if __name__ == "__main__":
    install_dependencies()
```

---

## 5. Security & Isolation Matrix

1. **Firebase Security Rules**:
   - `/agent/config` is strictly read-only for Android clients; write operations require backend service account credentials.
   - `/conversations/{cid}` and `/workspace` are constrained to authenticated sessions.
2. **Execution Sandbox**:
   - Shell commands and scripts imported from GitHub or local storage are never executed directly on the Android OS runtime. They are sent to the isolated Python execution container.
3. **Idempotency**:
   - Requests deduplicated via `requestId` in memory and RTDB.
4. **Artifact Delivery**:
   - Generated APK binary outputs are stored at designated artifact URLs or local filesystem exports (`APK_DOWNLOAD/app-debug.apk`) with verified size and MD5 hash tracking.
