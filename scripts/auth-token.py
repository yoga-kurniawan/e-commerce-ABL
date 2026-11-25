#!/usr/bin/env python3
"""Local CLI login; stdout is only the access token, suitable for shell assignment."""
import json
import sys
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import Request, urlopen

users = {"customer-a": "CUSTOMER_A_PASSWORD", "customer-b": "CUSTOMER_B_PASSWORD", "api-admin": "API_ADMIN_PASSWORD"}
if len(sys.argv) != 2 or sys.argv[1] not in users:
    sys.exit("Usage: python3 scripts/auth-token.py customer-a|customer-b|api-admin")
try:
    values = dict(line.split("=", 1) for line in (Path(__file__).resolve().parent.parent / ".secrets/auth.env").read_text().splitlines() if line)
    data = urlencode({"grant_type": "password", "client_id": "ecommerce-cli", "username": sys.argv[1], "password": values[users[sys.argv[1]]]}).encode()
    request = Request("http://localhost:18090/realms/ecommerce/protocol/openid-connect/token", data=data)
    with urlopen(request, timeout=20) as response:
        print(json.load(response)["access_token"])
except Exception as failure:
    # Do not print passwords, tokens, request bodies or full HTTP errors.
    sys.exit(f"Login gagal ({type(failure).__name__}). Cek auth-local up dan port-forward identity.")
