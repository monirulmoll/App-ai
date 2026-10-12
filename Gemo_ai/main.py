#!/usr/bin/env python3
"""
main.py — Main Entry Point for Gemo AI Autonomous Backend
Allows launching the backend with:
    python main.py
or with options:
    python main.py --firebase-url <URL>
"""

import sys
import argparse
from config import config
from Autonomous import AutonomousAgentEngine

def main():
    parser = argparse.ArgumentParser(
        description=f"{config.agent_name} Autonomous Backend Engine (Single Root /users/ Architecture)"
    )
    parser.add_argument(
        "--firebase-url",
        type=str,
        default=None,
        help="Optional Firebase RTDB URL override (defaults to backend_config.json or FIREBASE_URL env var)"
    )
    args = parser.parse_args()

    engine = AutonomousAgentEngine(firebase_url=args.firebase_url)
    engine.start()

if __name__ == "__main__":
    main()
