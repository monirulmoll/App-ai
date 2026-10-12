"""
config.py — Centralized Configuration Manager for Gemo AI Autonomous Backend
Loads settings from backend_config.json, environment variables, or default fallbacks.
Guarantees clean configuration without hard-coded constants in execution files.
"""

import os
import json
from typing import Dict, Any, Optional

DEFAULT_FIREBASE_URL = "https://ussr-error-404-default-rtdb.firebaseio.com"
DEFAULT_CONFIG_PATH = os.path.abspath(os.path.join(os.path.dirname(__file__), "backend_config.json"))

class BackendConfig:
    def __init__(self, config_file: Optional[str] = None):
        self.config_path = config_file or DEFAULT_CONFIG_PATH
        self._raw_data: Dict[str, Any] = {}
        self.load()

    def load(self):
        if os.path.exists(self.config_path):
            try:
                with open(self.config_path, "r", encoding="utf-8") as f:
                    self._raw_data = json.load(f)
            except Exception as e:
                self._raw_data = {}
        else:
            self._raw_data = {}

    @property
    def firebase_url(self) -> str:
        # Priority: Env variable > config file > fallback
        return (
            os.getenv("FIREBASE_URL")
            or self._raw_data.get("firebase", {}).get("database_url")
            or DEFAULT_FIREBASE_URL
        ).strip().rstrip("/")

    @property
    def http_timeout(self) -> int:
        return int(
            os.getenv("FIREBASE_TIMEOUT")
            or self._raw_data.get("firebase", {}).get("timeout_seconds")
            or 15
        )

    @property
    def poll_interval(self) -> float:
        return float(self._raw_data.get("firebase", {}).get("poll_interval_seconds") or 0.5)

    @property
    def terminal_poll_interval(self) -> float:
        return float(self._raw_data.get("firebase", {}).get("terminal_poll_interval_seconds") or 0.4)

    @property
    def root_node(self) -> str:
        return (self._raw_data.get("schema", {}).get("root_node") or "users").strip("/")

    @property
    def maker(self) -> str:
        return self._raw_data.get("agent", {}).get("maker") or "Rohit"

    @property
    def agent_name(self) -> str:
        return self._raw_data.get("agent", {}).get("name") or "Gemo AI"

    @property
    def default_model(self) -> str:
        return self._raw_data.get("agent", {}).get("default_model") or "Qwen 2.5 1.5B Instruct"

    @property
    def workspace_dir(self) -> str:
        rel = self._raw_data.get("agent", {}).get("workspace_dir") or "workspace"
        return os.path.abspath(os.path.join(os.path.dirname(__file__), rel))

    def get_user_path(self, user_id: str, sub_path: str = "") -> str:
        """Returns normalized Firebase path under the single root node."""
        clean_user = user_id.strip("/")
        if not sub_path:
            return f"{self.root_node}/{clean_user}"
        return f"{self.root_node}/{clean_user}/{sub_path.strip('/')}"

# Global singleton
config = BackendConfig()
