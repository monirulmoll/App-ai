"""
Setup.py — Dependency and Environment Setup for Gemo AI Autonomous Engine
Prepares isolated environment, workspace directories, configuration, and verifies runtime dependencies.
Zero external pip dependencies required — engine runs seamlessly on Python 3 standard library.
"""

import os
import sys
import subprocess
from config import config
from logger import GemoLogger

OPTIONAL_PACKAGES = [
    "requests",
    "rich"
]

def check_and_setup_environment():
    GemoLogger.banner(
        engine_name=config.agent_name,
        version="5.0.0-PROD",
        maker=config.maker,
        db_url=config.firebase_url,
        root_node=config.root_node
    )
    GemoLogger.section("Environment & Dependency Verification")

    # 1. Verify and create workspace directory
    ws_dir = config.workspace_dir
    os.makedirs(ws_dir, exist_ok=True)
    GemoLogger.info(f"Workspace directory verified: {ws_dir}")

    # 2. Check Python version
    py_ver = f"{sys.version_info.major}.{sys.version_info.minor}.{sys.version_info.micro}"
    GemoLogger.info(f"Python Runtime: {sys.executable} (v{py_ver})")

    # 3. Test core standard library packages
    std_libs = ["urllib.request", "json", "threading", "subprocess", "hashlib", "uuid", "time"]
    for lib in std_libs:
        try:
            __import__(lib.split('.')[0])
        except ImportError as e:
            GemoLogger.error(f"Missing core standard library: {lib}", e)
            sys.exit(1)
    GemoLogger.info(f"All core standard libraries verified ({len(std_libs)} modules)")

    # 4. Check optional packages
    pip_available = False
    try:
        subprocess.check_call([sys.executable, "-m", "pip", "--version"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        pip_available = True
    except Exception:
        pip_available = False

    if pip_available:
        for pkg in OPTIONAL_PACKAGES:
            try:
                subprocess.check_call([sys.executable, "-m", "pip", "install", pkg], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                GemoLogger.info(f"Optional package installed/verified: {pkg}")
            except Exception:
                pass
    else:
        GemoLogger.info("Note: Running in zero-dependency pure Python 3 mode (pip not required).")

    # 5. Check Firebase RTDB endpoint reachability
    GemoLogger.section("Firebase Database Ping")
    try:
        import urllib.request
        url = f"{config.firebase_url}/.json?shallow=true"
        req = urllib.request.Request(url, headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(req, timeout=10) as resp:
            GemoLogger.connection_status(connected=True, url=config.firebase_url)
    except Exception as e:
        GemoLogger.connection_status(connected=False, url=config.firebase_url, error_msg=str(e))

    GemoLogger.section("Setup Complete")
    GemoLogger.info(f"Backend is ready! Run with: python main.py")

if __name__ == "__main__":
    check_and_setup_environment()
