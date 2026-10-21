"""Prints N one-time enrolment codes and the ENROLL_HASHES value for the gateway.

Give each code to one officer's phone; keep only the hashes on the server.
Usage: python tools/make_codes.py 10
"""
import hashlib
import secrets
import sys

n = int(sys.argv[1]) if len(sys.argv) > 1 else 1
codes = [secrets.token_urlsafe(9) for _ in range(n)]
for c in codes:
    print(c)
print("\nENROLL_HASHES=" + ",".join(hashlib.sha256(c.encode()).hexdigest() for c in codes))
