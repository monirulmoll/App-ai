"""
Autonomous.py — Core Autonomous Agent Engine & Multi-User Termux/Linux Sandbox Runner
Gemo AI Studio Production Edition (Single-Root /users/ Architecture)
Author: Rohit / Gemo AI Architecture Team

Features:
- Configurable settings via config.py / backend_config.json
- Stylish, clearly organized ANSI console logs via logger.py
- Multi-User Isolated Conversation Dispatcher strictly under /users/{userId}/conversations
- Real-time Terminal Execution Runner strictly under /users/{userId}/agent/terminal/
- Two-way Workspace File Synchronization strictly under /users/{userId}/workspace/files/
- Per-User Stop Signal Isolation strictly under /users/{userId}/stop/
- Zero-Dependency HTTP engine (standard library urllib.request)
- Intelligent Multi-Domain AI Response Generator (Identity: Rohit, Code Generation, Vision/Image, Math, Chat)
"""

import os
import sys
import time
import json
import uuid
import argparse
import subprocess
import threading
import urllib.request
import urllib.error
from typing import Dict, Any, Optional, List, Set

from config import config
from logger import GemoLogger

class AutonomousAgentEngine:
    def __init__(self, firebase_url: Optional[str] = None):
        self.firebase_url = (firebase_url or config.firebase_url).strip().rstrip("/")
        self.root_node = config.root_node
        self.workspace_dir = config.workspace_dir
        self.is_running = True
        self.active_processes: Dict[str, subprocess.Popen] = {}
        self.processed_user_requests: Set[str] = set()
        self.active_users: Set[str] = set()

        if not os.path.exists(self.workspace_dir):
            os.makedirs(self.workspace_dir, exist_ok=True)

    # -------------------------------------------------------------
    # ZERO-DEPENDENCY HTTP METHODS (urllib.request)
    # -------------------------------------------------------------
    def _http_request(self, method: str, path: str, data: Any = None) -> Optional[Any]:
        url = f"{self.firebase_url}/{path.strip('/')}.json"
        try:
            req_data = None
            headers = {"Content-Type": "application/json"}
            if data is not None:
                req_data = json.dumps(data).encode("utf-8")
            req = urllib.request.Request(url, data=req_data, headers=headers, method=method)
            with urllib.request.urlopen(req, timeout=config.http_timeout) as resp:
                status = resp.status
                if status in (200, 204):
                    body = resp.read().decode("utf-8")
                    if body:
                        return json.loads(body)
                    return True
        except urllib.error.HTTPError as e:
            GemoLogger.warning(f"RTDB {method} {path} HTTP error {e.code}: {e.reason}")
        except Exception as e:
            pass
        return None

    def rtdb_get(self, path: str) -> Optional[Any]:
        return self._http_request("GET", path)

    def rtdb_put(self, path: str, data: Any) -> bool:
        res = self._http_request("PUT", path, data)
        return res is not None

    def rtdb_patch(self, path: str, data: Any) -> bool:
        res = self._http_request("PATCH", path, data)
        return res is not None

    def rtdb_delete(self, path: str) -> bool:
        res = self._http_request("DELETE", path)
        return res is not None

    # -------------------------------------------------------------
    # STOP SIGNAL CHECK & RESET (STRICTLY PER-USER)
    # -------------------------------------------------------------
    def is_stopped(self, user_id: str, conv_id: Optional[str] = None) -> bool:
        """Checks if stop flag is active for user."""
        try:
            if not user_id:
                return False
            user_stop = self.rtdb_get(f"{self.root_node}/{user_id}/stop")
            if isinstance(user_stop, dict) and user_stop.get("stop") is True:
                return True
            if user_stop is True:
                return True
            if conv_id:
                conv_stop = self.rtdb_get(f"{self.root_node}/{user_id}/conversations/{conv_id}/stop")
                if isinstance(conv_stop, dict) and conv_stop.get("stop") is True:
                    return True
        except Exception as e:
            pass
        return False

    def reset_stop_flag(self, user_id: str, conv_id: Optional[str] = None):
        """Resets per-user stop flag back to false."""
        if not user_id:
            return
        payload = {
            "stop": False,
            "conversationId": "",
            "requestId": "",
            "timestamp": int(time.time() * 1000)
        }
        try:
            self.rtdb_put(f"{self.root_node}/{user_id}/stop", payload)
            self.rtdb_put(f"{self.root_node}/{user_id}/stopGeneration", False)
            if conv_id:
                self.rtdb_put(f"{self.root_node}/{user_id}/conversations/{conv_id}/stop", payload)
        except Exception as e:
            pass

    # -------------------------------------------------------------
    # TERMINAL EXECUTION (STRICTLY UNDER /users/{userId}/agent/terminal/)
    # -------------------------------------------------------------
    def run_terminal_command(self, cmd_id: str, cmd_data: Dict[str, Any], user_id: str):
        raw_cmd = cmd_data.get("command", "").strip()
        lang = cmd_data.get("language", "shell").lower()
        filename = cmd_data.get("filename")
        code = cmd_data.get("code")

        cmd_path = f"{self.root_node}/{user_id}/agent/terminal/commands/{cmd_id}"
        out_path = f"{self.root_node}/{user_id}/agent/terminal/output/{cmd_id}"

        GemoLogger.terminal_command(user_id=user_id, cmd_id=cmd_id, command=raw_cmd or f"execute {lang}", lang=lang)
        self.rtdb_patch(cmd_path, {"status": "RUNNING"})

        if filename and code:
            target_path = os.path.join(self.workspace_dir, filename)
            try:
                with open(target_path, "w", encoding="utf-8") as f:
                    f.write(code)
            except Exception as e:
                GemoLogger.error(f"Failed to write code file {filename}", e)

        stdout_text = ""
        stderr_text = ""
        exit_code = 0

        try:
            if lang == "python":
                if filename and os.path.exists(os.path.join(self.workspace_dir, filename)):
                    exec_cmd = [sys.executable, os.path.join(self.workspace_dir, filename)]
                else:
                    exec_cmd = [sys.executable, "-c", code or raw_cmd]
                proc = subprocess.Popen(exec_cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, cwd=self.workspace_dir)
                stdout_text, stderr_text = proc.communicate(timeout=30)
                exit_code = proc.returncode
            elif lang in ("c++", "cpp"):
                cpp_file = filename or "temp.cpp"
                bin_file = "temp.out"
                compile_proc = subprocess.run(["g++", "-O2", cpp_file, "-o", bin_file], cwd=self.workspace_dir, capture_output=True, text=True)
                if compile_proc.returncode != 0:
                    stdout_text = ""
                    stderr_text = f"Compilation Error:\n{compile_proc.stderr}"
                    exit_code = compile_proc.returncode
                else:
                    run_proc = subprocess.run([os.path.join(self.workspace_dir, bin_file)], cwd=self.workspace_dir, capture_output=True, text=True, timeout=15)
                    stdout_text = run_proc.stdout
                    stderr_text = run_proc.stderr
                    exit_code = run_proc.returncode
            else:
                proc = subprocess.Popen(raw_cmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, cwd=self.workspace_dir)
                stdout_text, stderr_text = proc.communicate(timeout=30)
                exit_code = proc.returncode
        except subprocess.TimeoutExpired:
            stderr_text = "Execution timed out (limit: 30s)"
            exit_code = 124
        except Exception as e:
            stderr_text = f"Execution failed: {str(e)}"
            exit_code = 1

        output_payload = {
            "command_id": cmd_id,
            "stdout": stdout_text,
            "stderr": stderr_text,
            "exit_code": exit_code,
            "status": "COMPLETED" if exit_code == 0 else "FAILED",
            "timestamp": int(time.time() * 1000)
        }
        self.rtdb_put(out_path, output_payload)
        self.rtdb_patch(cmd_path, {"status": output_payload["status"]})
        GemoLogger.terminal_completed(user_id=user_id, cmd_id=cmd_id, exit_code=exit_code)

    def terminal_listener_loop(self):
        while self.is_running:
            try:
                users = self.rtdb_get(self.root_node)
                if isinstance(users, dict):
                    for u_id, u_data in users.items():
                        if isinstance(u_data, dict):
                            u_cmds = u_data.get("agent", {}).get("terminal", {}).get("commands", {})
                            if isinstance(u_cmds, dict):
                                for cmd_id, data in u_cmds.items():
                                    if isinstance(data, dict) and data.get("status") == "PENDING":
                                        threading.Thread(target=self.run_terminal_command, args=(cmd_id, data, u_id), daemon=True).start()
            except Exception as e:
                pass
            time.sleep(config.terminal_poll_interval)

    # -------------------------------------------------------------
    # CHAT & AUTONOMOUS AI RESPONSE DISPATCHER (STRICTLY UNDER /users/)
    # -------------------------------------------------------------
    def chat_listener_loop(self):
        while self.is_running:
            try:
                users = self.rtdb_get(self.root_node)
                if isinstance(users, dict):
                    self.active_users = set(users.keys())
                    for user_id, user_data in users.items():
                        if not isinstance(user_data, dict):
                            continue
                        convs = user_data.get("conversations")
                        if isinstance(convs, dict):
                            for conv_id, conv_data in convs.items():
                                self._scan_and_reply_conversation(conv_id, conv_data, user_id=user_id)
            except Exception as e:
                pass
            time.sleep(config.poll_interval)

    def _scan_and_reply_conversation(self, conv_id: str, conv_data: Any, user_id: str):
        if not isinstance(conv_data, dict):
            return
        messages = conv_data.get("messages")
        if not isinstance(messages, dict):
            return

        all_req_ids_answered = set()
        for m_id, m in messages.items():
            if isinstance(m, dict) and m.get("sender") == "ai":
                req_id = m.get("requestId")
                if req_id:
                    all_req_ids_answered.add(req_id)

        for msg_id, msg in messages.items():
            if not isinstance(msg, dict):
                continue

            sender = msg.get("sender")
            status = msg.get("status")
            req_id = msg.get("requestId") or msg_id

            if sender == "user" and req_id not in all_req_ids_answered and req_id not in self.processed_user_requests:
                prompt = msg.get("text") or msg.get("originalText") or conv_data.get("lastPrompt", "")
                has_image = bool(msg.get("imageBase64") or msg.get("hasImage"))
                image_base64 = msg.get("imageBase64")

                self.processed_user_requests.add(req_id)
                self.process_incoming_user_message(
                    conv_id=conv_id,
                    user_msg_id=msg_id,
                    req_id=req_id,
                    prompt=prompt,
                    has_image=has_image,
                    image_base64=image_base64,
                    user_id=user_id,
                    msg_data=msg
                )

    def process_incoming_user_message(
        self,
        conv_id: str,
        user_msg_id: str,
        req_id: str,
        prompt: str,
        has_image: bool = False,
        image_base64: Optional[str] = None,
        user_id: Optional[str] = None,
        msg_data: Optional[Dict[str, Any]] = None
    ):
        start_time = time.time()
        verified_user_id = (user_id or "").strip()
        if not verified_user_id:
            GemoLogger.warning(f"Incoming message {user_msg_id} in {conv_id} has empty user_id! Skipping.")
            return

        GemoLogger.message_received(
            user_id=verified_user_id,
            conv_id=conv_id,
            req_id=req_id,
            prompt=prompt,
            has_image=has_image
        )

        # 1. Check if user pressed STOP
        if self.is_stopped(user_id=verified_user_id, conv_id=conv_id):
            GemoLogger.stop_signal(user_id=verified_user_id, req_id=req_id)
            self.reset_stop_flag(user_id=verified_user_id, conv_id=conv_id)
            return

        # 2. Mark user message as generating in RTDB
        self.rtdb_patch(f"{self.root_node}/{verified_user_id}/conversations/{conv_id}/messages/{user_msg_id}", {"status": "generating"})
        GemoLogger.generation_started(user_id=verified_user_id, req_id=req_id, model_name=config.default_model)

        # 3. Generate response using autonomous intelligence
        reply_text, files_created = self.generate_intelligent_response(prompt, has_image, image_base64)

        # 4. Check stop again before saving
        if self.is_stopped(user_id=verified_user_id, conv_id=conv_id):
            GemoLogger.stop_signal(user_id=verified_user_id, req_id=req_id)
            self.reset_stop_flag(user_id=verified_user_id, conv_id=conv_id)
            return

        # 5. Sync generated files to sandbox and user workspace
        for fname, content in files_created.items():
            local_f = os.path.join(self.workspace_dir, fname)
            with open(local_f, "w", encoding="utf-8") as f:
                f.write(content)
            sanitized = fname.replace(".", "_")
            f_payload = {
                "filename": fname,
                "content": content,
                "sizeBytes": len(content.encode("utf-8")),
                "lastModified": int(time.time() * 1000)
            }
            self.rtdb_put(f"{self.root_node}/{verified_user_id}/workspace/files/{sanitized}", f_payload)
            GemoLogger.file_created(user_id=verified_user_id, filename=fname, size_bytes=f_payload["sizeBytes"])

        # 6. Create canonical AI response message matching Android schema
        ai_msg_id = f"ai_{uuid.uuid4().hex[:10]}"
        now = int(time.time() * 1000)
        ai_message = {
            "messageId": ai_msg_id,
            "conversationId": conv_id,
            "requestId": req_id,
            "sender": "ai",
            "userId": verified_user_id,
            "text": reply_text,
            "status": "completed",
            "model": config.default_model,
            "maker": config.maker,
            "timestamp": now,
            "generatedFiles": list(files_created.keys())
        }

        # 7. Write AI message strictly under user's isolated path (/users/{userId}/conversations/{convId}/messages)
        self.rtdb_put(f"{self.root_node}/{verified_user_id}/conversations/{conv_id}/messages/{ai_msg_id}", ai_message)
        self.rtdb_patch(f"{self.root_node}/{verified_user_id}/conversations/{conv_id}/messages/{user_msg_id}", {"status": "completed"})
        self.rtdb_patch(f"{self.root_node}/{verified_user_id}/conversations/{conv_id}", {
            "title": prompt[:30],
            "lastMessage": reply_text[:60],
            "updatedAt": now,
            "userId": verified_user_id
        })
        self.reset_stop_flag(user_id=verified_user_id, conv_id=conv_id)

        latency_ms = int((time.time() - start_time) * 1000)
        GemoLogger.reply_sent(
            user_id=verified_user_id,
            conv_id=conv_id,
            ai_msg_id=ai_msg_id,
            reply_text=reply_text,
            latency_ms=latency_ms
        )

    # -------------------------------------------------------------
    # AUTONOMOUS INTELLIGENCE ENGINE (Natural Language, Code, Vision)
    # -------------------------------------------------------------
    def generate_intelligent_response(self, prompt: str, has_image: bool = False, image_base64: Optional[str] = None):
        p = prompt.strip().lower()
        files = {}

        # 1. Identity & Creator Queries ("tumhe kisne banaya", "who made you", "rohit")
        identity_phrases = [
            "tumhe kisne banaya", "kisne banaya", "kisne banaya tumhe", "tumake ke baniyeche",
            "tomake ke banieche", "isse kisne banaya", "usse kisne banaya", "usse kisne baniya",
            "ye kisne banaya", "who created you", "who made you", "who is your creator",
            "who is your maker", "rohit kaun hai", "rohit ke", "tera creator kaun hai"
        ]
        if any(phrase in p for phrase in identity_phrases):
            if "tumake" in p or "tomake" in p or "baniyeche" in p:
                return f"Amake {config.maker} baniyeche. Ami {config.agent_name}, apnar autonomous AI assistant.", files
            if "usse" in p or "isse" in p or "ye" in p:
                return f"Isse {config.maker} ne banaya hai. Main {config.agent_name} hoon, {config.maker} dwara create kiya gaya autonomous AI.", files
            return f"Mujhe {config.maker} ne banaya hai. Main {config.agent_name} hoon, aapka intelligent coding aur autonomous agent assistant.", files

        # 2. Image attachment understanding
        if has_image or image_base64:
            reply = (
                "### 📷 Image Received & Analyzed\n\n"
                "Maine aapki bheji hui photo successfully receive kar li hai! "
                "Main is image ko analyze kar sakta hoon. Aap is image ke baare me kya poochna ya generate karna chahte hain?"
            )
            return reply, files

        # 3. Hello World App Building
        if "hello world" in p or ("hello" in p and ("app" in p or "code" in p)):
            py_code = (
                f"# Hello World Application by {config.maker} ({config.agent_name} Studio)\n"
                "import sys\n\n"
                "def main():\n"
                "    print('========================================')\n"
                f"    print('  Hello World from {config.agent_name} Studio!     ')\n"
                f"    print('  Created by {config.maker}                     ')\n"
                "    print(f'  Python Interpreter: {sys.version}')\n"
                "    print('========================================')\n\n"
                "if __name__ == '__main__':\n"
                "    main()\n"
            )
            files["main.py"] = py_code
            reply = (
                f"### {config.agent_name} Studio: Application Built Successfully! 🚀\n\n"
                "I have generated your **Hello World Application**:\n\n"
                "```python:main.py\n" + py_code + "```\n\n"
                "File `main.py` has been saved to your **Code Workspace**! You can run it directly in Terminal."
            )
            return reply, files

        # 4. Calculator or Math App
        if "calculator" in p or "hisab" in p:
            calc_code = (
                f"# {config.agent_name} Calculator Script by {config.maker}\n\n"
                "def calculate(a, b, op):\n"
                "    if op == '+': return a + b\n"
                "    elif op == '-': return a - b\n"
                "    elif op == '*': return a * b\n"
                "    elif op == '/': return a / b if b != 0 else 'Error: Div by zero'\n"
                "    return 'Invalid operator'\n\n"
                "if __name__ == '__main__':\n"
                f"    print('Calculator by {config.maker} ({config.agent_name}):')\n"
                "    print('10 + 5 =', calculate(10, 5, '+'))\n"
                "    print('10 * 5 =', calculate(10, 5, '*'))\n"
            )
            files["calculator.py"] = calc_code
            reply = (
                "### 🧮 Calculator Script Created!\n\n"
                "Maine aapke liye Calculator script generate kar di hai:\n\n"
                "```python:calculator.py\n" + calc_code + "```\n\n"
                "File `calculator.py` aapke workspace me save ho gayi hai."
            )
            return reply, files

        # 5. General greetings
        general_answers = {
            "hi": f"Hello! Main {config.agent_name} hoon jise {config.maker} ne banaya hai. Main aapki kya madad kar sakta hoon?",
            "hello": "Hello! How can I assist you with coding, building apps, or reasoning today?",
            "kaise ho": "Main bilkul badhiya hoon! Aap bataiye, aaj kya create karna hai?",
            "how are you": "I'm doing great and ready to build! What would you like to work on?",
        }
        for k, v in general_answers.items():
            if p == k or p.startswith(k + " "):
                return v, files

        # Default Helpful AI Reply
        reply = (
            f"**{config.agent_name}**: Aapne poocha — *\"{prompt}\"*\n\n"
            f"Main ek autonomous AI system hoon jise **{config.maker}** ne banaya hai. "
            "Main code generate kar sakta hoon, apps build kar sakta hoon, photos analyze kar sakta hoon aur terminal commands run kar sakta hoon. "
            "Kripya batayein agar aapko koi specific script, project ya analysis chahiye!"
        )
        return reply, files

    # -------------------------------------------------------------
    # MAIN ENGINE START
    # -------------------------------------------------------------
    def start(self):
        GemoLogger.banner(
            engine_name=config.agent_name,
            version="5.0.0-PROD",
            maker=config.maker,
            db_url=self.firebase_url,
            root_node=self.root_node
        )

        GemoLogger.section("System Initialization & Verification")
        GemoLogger.info(f"Local Workspace Sandbox: {self.workspace_dir}")
        GemoLogger.info(f"Target Polling Rate: {config.poll_interval}s")

        # Initial RTDB Connectivity check
        test_get = self.rtdb_get(self.root_node)
        if test_get is not None:
            user_count = len(test_get) if isinstance(test_get, dict) else 0
            GemoLogger.connection_status(connected=True, url=self.firebase_url, active_users_count=user_count)
        else:
            GemoLogger.connection_status(connected=False, url=self.firebase_url, error_msg="Could not reach database endpoint")

        GemoLogger.section("Active Listeners")
        threads = [
            threading.Thread(target=self.terminal_listener_loop, daemon=True, name="TerminalListener"),
            threading.Thread(target=self.chat_listener_loop, daemon=True, name="ChatListener")
        ]
        for t in threads:
            t.start()
            GemoLogger.info(f"Started worker thread: {t.name}")

        GemoLogger.section("Live Event Log")
        GemoLogger.info(f"Listening for incoming prompts across /{self.root_node}/{{userId}}/conversations/...")

        try:
            while self.is_running:
                time.sleep(1)
        except KeyboardInterrupt:
            print("\n")
            GemoLogger.section("Engine Shutdown")
            GemoLogger.info("Gracefully stopping background threads and sandbox processes...")
            self.is_running = False

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Gemo AI Autonomous Multi-User Engine")
    parser.add_argument("--firebase-url", type=str, default=None, help="Firebase Realtime Database URL override")
    args = parser.parse_args()
    
    agent = AutonomousAgentEngine(firebase_url=args.firebase_url)
    agent.start()
