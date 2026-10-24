"""Prints N one-time enrolment codes and the ENROLL_HASHES line; only the hashes go on the server."""
import hashlib
import secrets
import sys

n = int(sys.argv[1]) if len(sys.argv) > 1 else 1
codes = [secrets.token_urlsafe(9) for _ in range(n)]
for c in codes:
    print(c)
print("\nENROLL_HASHES=" + ",".join(hashlib.sha256(c.encode()).hexdigest() for c in codes))
