import http.cookiejar
import re
import urllib.parse
import urllib.request

BASE = "http://localhost:8080"
USER = "apichk_tmp"
PASS = "TmpPass123!"

jar = http.cookiejar.CookieJar()
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))


def get(path):
    with opener.open(BASE + path) as r:
        return r.status, r.read().decode("utf-8", "replace")


def raw(req_path, **kw):
    try:
        with opener.open(req) as r:
            return r.status, r.headers.get("content-type"), r.read()[:160]
    except urllib.error.HTTPError as e:
        return e.code, e.headers.get("content-type"), e.read()[:160]


def show(label, req):
    try:
        with opener.open(req) as r:
            print(f"{label}: {r.status} | {r.headers.get('content-type')} | {r.read()[:150]}")
    except urllib.error.HTTPError as e:
        print(f"{label}: {e.code} | {e.headers.get('content-type')} | {e.read()[:150]}")


st, html = get("/login")
tok = re.search(r'name="_csrf"\s+value="([^"]+)"', html).group(1)
req = urllib.request.Request(BASE + "/login",
                             data=urllib.parse.urlencode(
                                 {"username": USER, "password": PASS, "_csrf": tok}).encode(),
                             method="POST")
req.add_header("Content-Type", "application/x-www-form-urlencoded")
with opener.open(req) as r:
    print("login:", r.status, r.url)

st, html = get("/chatbot")
masked = re.search(r'window\.FW_CSRF_TOKEN\s*=\s*"([^"]*)"', html).group(1)
print("token:", masked[:30])

# 1. DELETE history with header token
req = urllib.request.Request(BASE + "/api/chatbot/history", method="DELETE")
req.add_header("X-XSRF-TOKEN", masked)
show("DELETE history (header token)", req)

# 2. POST ask with JSON + header token (expect 200)
req = urllib.request.Request(BASE + "/api/chatbot/ask", data=b'{"message":"Show Balance"}', method="POST")
req.add_header("Content-Type", "application/json")
req.add_header("Accept", "application/json")
req.add_header("X-XSRF-TOKEN", masked)
show("POST ask JSON (header token)", req)

# 3. POST ask form-encoded + _csrf param -> if csrf ok, expect 400/415 not 403
req = urllib.request.Request(BASE + "/api/chatbot/ask?_csrf=" + urllib.parse.quote(masked),
                             data=b"message=hi", method="POST")
req.add_header("Content-Type", "application/x-www-form-urlencoded")
show("POST ask FORM (+csrf param)", req)

# 4. POST ask form-encoded WITHOUT csrf -> expect 403
req = urllib.request.Request(BASE + "/api/chatbot/ask", data=b"message=hi", method="POST")
req.add_header("Content-Type", "application/x-www-form-urlencoded")
show("POST ask FORM (no csrf)", req)

# 5. another authenticated POST endpoint (settings?) to compare
st, html = get("/chatbot")
masked2 = re.search(r'window\.FW_CSRF_TOKEN\s*=\s*"([^"]*)"', html).group(1)
req = urllib.request.Request(BASE + "/api/chatbot/history", method="DELETE")
req.add_header("X-XSRF-TOKEN", masked2)
show("DELETE history round2", req)
