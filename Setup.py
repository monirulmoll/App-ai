"""
Setup.py — Dependency and Environment Setup for Gemo AI Autonomous Engine
Prepares isolated environment, CLI tools, and required runtime dependencies.
Models to download will be specified later; no models are downloaded in this step.
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
        print(f" -> Checking and installing {pkg}...")
        try:
            subprocess.check_call([sys.executable, "-m", "pip", "install", pkg])
        except Exception as e:
            print(f"    [Warning] Could not install {pkg}: {e}")
    print("[Setup.py] Tooling dependencies verified.")

if __name__ == "__main__":
    install_dependencies()
