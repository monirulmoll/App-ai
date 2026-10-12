"""
logger.py — Stylish, ANSI-Color Terminal Logger for Gemo AI Autonomous Backend
Provides organized, aesthetic boxes and badges for:
  - Startup & System Architecture
  - Firebase RTDB Connection Status
  - Incoming Prompts & User Isolation Tracking
  - AI Generation & Workspace File Creation
  - Reply Delivery & Latency
  - Warnings & Errors
"""

import sys
import time
from typing import Optional, List, Dict, Any

class TerminalColors:
    RESET = "\033[0m"
    BOLD = "\033[1m"
    DIM = "\033[2m"
    ITALIC = "\033[3m"
    UNDERLINE = "\033[4m"
    
    # Foreground
    BLACK = "\033[30m"
    RED = "\033[91m"
    GREEN = "\033[92m"
    YELLOW = "\033[93m"
    BLUE = "\033[94m"
    MAGENTA = "\033[95m"
    CYAN = "\033[96m"
    WHITE = "\033[97m"
    GRAY = "\033[90m"
    
    # Background
    BG_BLUE = "\033[44m"
    BG_MAGENTA = "\033[45m"
    BG_CYAN = "\033[46m"
    BG_DARK = "\033[100m"

class GemoLogger:
    @staticmethod
    def _timestamp() -> str:
        return time.strftime("%H:%M:%S")

    @classmethod
    def banner(cls, engine_name: str, version: str, maker: str, db_url: str, root_node: str):
        c = TerminalColors
        line_w = 64
        print(f"\n{c.CYAN}{'═' * line_w}{c.RESET}")
        print(f"{c.BOLD}{c.CYAN}   🚀 {engine_name.upper()} AUTONOMOUS ENGINE {c.RESET}{c.MAGENTA}v{version}{c.RESET}")
        print(f"{c.GRAY}   Architected & Created by: {c.BOLD}{c.WHITE}{maker}{c.RESET}")
        print(f"{c.GRAY}   Firebase Schema: {c.BOLD}{c.GREEN}/{root_node.strip('/')}/{{userId}}/{c.RESET}")
        print(f"{c.GRAY}   Realtime Database: {c.BLUE}{db_url}{c.RESET}")
        print(f"{c.CYAN}{'═' * line_w}{c.RESET}\n")

    @classmethod
    def section(cls, title: str):
        c = TerminalColors
        print(f"\n{c.BOLD}{c.MAGENTA}▶ {title.upper()}{c.RESET} {c.GRAY}{'─' * (55 - len(title))}{c.RESET}")

    @classmethod
    def connection_status(cls, connected: bool, url: str, active_users_count: int = 0, error_msg: Optional[str] = None):
        c = TerminalColors
        ts = cls._timestamp()
        if connected:
            badge = f"{c.BOLD}{c.GREEN}[CONNECTED]{c.RESET}"
            detail = f"{c.WHITE}{url}{c.RESET} {c.GRAY}(Active Accounts: {c.CYAN}{active_users_count}{c.GRAY}){c.RESET}"
        else:
            badge = f"{c.BOLD}{c.RED}[OFFLINE]{c.RESET}"
            detail = f"{c.RED}{url}{c.RESET} {c.GRAY}(Reason: {error_msg or 'Network / Host Unreachable'}){c.RESET}"
        print(f"{c.GRAY}[{ts}]{c.RESET} {badge} {detail}")

    @classmethod
    def message_received(cls, user_id: str, conv_id: str, req_id: str, prompt: str, has_image: bool = False):
        c = TerminalColors
        ts = cls._timestamp()
        user_badge = f"{c.BOLD}{c.CYAN}@{user_id}{c.RESET}"
        short_prompt = prompt.replace("\n", " ")
        if len(short_prompt) > 70:
            short_prompt = short_prompt[:67] + "..."
        img_badge = f" {c.YELLOW}[📷 Image]{c.RESET}" if has_image else ""
        
        print(f"\n{c.GRAY}[{ts}]{c.RESET} {c.BOLD}{c.BLUE}📩 INCOMING MESSAGE{c.RESET} from {user_badge}{img_badge}")
        print(f"       {c.GRAY}├─ Conversation :{c.RESET} {conv_id}")
        print(f"       {c.GRAY}├─ Request ID   :{c.RESET} {req_id}")
        print(f"       {c.GRAY}└─ Prompt       :{c.RESET} {c.WHITE}\"{short_prompt}\"{c.RESET}")

    @classmethod
    def generation_started(cls, user_id: str, req_id: str, model_name: str):
        c = TerminalColors
        ts = cls._timestamp()
        print(f"{c.GRAY}[{ts}]{c.RESET} {c.YELLOW}⚡ PROCESSING{c.RESET} {c.GRAY}[{model_name}]{c.RESET} for {c.CYAN}@{user_id}{c.RESET} (req={req_id[:12]})")

    @classmethod
    def file_created(cls, user_id: str, filename: str, size_bytes: int):
        c = TerminalColors
        ts = cls._timestamp()
        print(f"{c.GRAY}[{ts}]{c.RESET} {c.GREEN}📁 WORKSPACE FILE{c.RESET} Saved {c.BOLD}{filename}{c.RESET} ({size_bytes}B) to sandbox & RTDB")

    @classmethod
    def reply_sent(cls, user_id: str, conv_id: str, ai_msg_id: str, reply_text: str, latency_ms: int):
        c = TerminalColors
        ts = cls._timestamp()
        user_badge = f"{c.BOLD}{c.CYAN}@{user_id}{c.RESET}"
        short_reply = reply_text.replace("\n", " ")
        if len(short_reply) > 75:
            short_reply = short_reply[:72] + "..."
        print(f"{c.GRAY}[{ts}]{c.RESET} {c.BOLD}{c.GREEN}✔ REPLY DELIVERED{c.RESET} to {user_badge} in {c.CYAN}{latency_ms}ms{c.RESET}")
        print(f"       {c.GRAY}├─ Path :{c.RESET} users/{user_id}/conversations/{conv_id}/messages/{ai_msg_id}")
        print(f"       {c.GRAY}└─ Text :{c.RESET} {c.WHITE}\"{short_reply}\"{c.RESET}")

    @classmethod
    def stop_signal(cls, user_id: str, req_id: str):
        c = TerminalColors
        ts = cls._timestamp()
        print(f"{c.GRAY}[{ts}]{c.RESET} {c.BOLD}{c.RED}🛑 STOP TRIGGERED{c.RESET} for user {c.CYAN}@{user_id}{c.RESET} (req={req_id}) — Aborted generation.")

    @classmethod
    def terminal_command(cls, user_id: str, cmd_id: str, command: str, lang: str):
        c = TerminalColors
        ts = cls._timestamp()
        print(f"{c.GRAY}[{ts}]{c.RESET} {c.MAGENTA}💻 TERMINAL CMD{c.RESET} [{lang}] from {c.CYAN}@{user_id}{c.RESET}: {c.WHITE}{command}{c.RESET}")

    @classmethod
    def terminal_completed(cls, user_id: str, cmd_id: str, exit_code: int):
        c = TerminalColors
        ts = cls._timestamp()
        status_color = c.GREEN if exit_code == 0 else c.RED
        print(f"{c.GRAY}[{ts}]{c.RESET} {status_color}💻 TERMINAL RESULT{c.RESET} [{cmd_id}] finished with exit code {exit_code}")

    @classmethod
    def warning(cls, message: str):
        c = TerminalColors
        ts = cls._timestamp()
        print(f"{c.GRAY}[{ts}]{c.RESET} {c.BOLD}{c.YELLOW}[WARNING]{c.RESET} {message}")

    @classmethod
    def error(cls, message: str, ex: Optional[Exception] = None):
        c = TerminalColors
        ts = cls._timestamp()
        err_str = f" - {str(ex)}" if ex else ""
        print(f"{c.GRAY}[{ts}]{c.RESET} {c.BOLD}{c.RED}[ERROR]{c.RESET} {message}{err_str}")

    @classmethod
    def info(cls, message: str):
        c = TerminalColors
        ts = cls._timestamp()
        print(f"{c.GRAY}[{ts}]{c.RESET} {c.CYAN}[INFO]{c.RESET} {message}")
