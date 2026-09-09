#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""管家婆─家庭收支管理系统 集成测试执行脚本（docs/集成测试用例.md 54 用例）"""
import json
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = "http://localhost:8080/api"
results = []  # (case_id, passed, note)
CAP = {}      # captured ids


def req(method, path, body=None, token=None, params=None):
    url = BASE + path
    if params:
        url += "?" + "&".join(
            f"{k}={urllib.parse.quote(str(v), safe='')}"
            for k, v in params.items() if v is not None)
    data = json.dumps(body).encode("utf-8") if body is not None else None
    r = urllib.request.Request(url, data=data, method=method)
    if body is not None:
        r.add_header("Content-Type", "application/json")
    if token:
        r.add_header("Authorization", token)
    try:
        with urllib.request.urlopen(r, timeout=30) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode("utf-8"))
        except Exception:
            return e.code, None


def db(sql):
    out = subprocess.run(
        ["mysql", "-h", "127.0.0.1", "-P", "3307", "-uroot", "-proot",
         "family_finance", "-N", "-e", sql],
        capture_output=True, text=True)
    if out.returncode != 0:
        raise RuntimeError("DB error: " + out.stderr)
    return out.stdout.strip()


def check(case, cond, note=""):
    results.append((case, bool(cond), note))
    print(f"{'PASS' if cond else 'FAIL'}  {case}  {note}")


def code_of(resp):
    return resp.get("code") if isinstance(resp, dict) else None


def msg_of(resp):
    return resp.get("message", "") if isinstance(resp, dict) else ""


def data_of(resp):
    return resp.get("data") if isinstance(resp, dict) else None


def find_by_name(lst, name):
    for x in lst or []:
        if x.get("name") == name or x.get("categoryName") == name:
            return x
    return None


def login(username, password):
    st, resp = req("POST", "/auth/login", {"username": username, "password": password})
    return resp, (data_of(resp) or {}).get("token")


# ================= 0. SEC-01 未登录拦截 =================
st, resp = req("GET", "/transactions")
check("SEC-01", code_of(resp) == 401 and "未登录" in msg_of(resp),
      f"code={code_of(resp)}, message={msg_of(resp)}")

# ================= 1. AUTH 认证与用户 =================
st, resp = req("POST", "/auth/register",
               {"username": "demo", "password": "demo123456",
                "nickname": "演示员", "familyName": "演示家庭"})
TOKEN = (data_of(resp) or {}).get("token")
role = ((data_of(resp) or {}).get("user") or {}).get("role")
check("AUTH-01", code_of(resp) == 200 and TOKEN and role == "ADMIN",
      f"code={code_of(resp)}, role={role}")
u, f, m, c = db("SELECT COUNT(*) FROM user"), db("SELECT COUNT(*) FROM family"), \
    db("SELECT COUNT(*) FROM family_member"), db("SELECT COUNT(*) FROM category")
check("AUTH-01-DB", u == "1" and f == "1" and m == "1" and int(c) >= 20,
      f"user={u}, family={f}, member={m}, category={c}（文档描述'约26条'，实际{c}条）")

st, resp = req("POST", "/auth/register",
               {"username": "demo", "password": "demo123456",
                "nickname": "演示员", "familyName": "演示家庭"})
check("AUTH-02", code_of(resp) == 400 and "用户名已存在" in msg_of(resp),
      f"code={code_of(resp)}, message={msg_of(resp)}")

resp, TOKEN = login("demo", "demo123456")
check("AUTH-03", code_of(resp) == 200 and bool(TOKEN), f"code={code_of(resp)}")

resp, _ = login("demo", "wrong")
check("AUTH-04", code_of(resp) == 400 and msg_of(resp) == "用户名或密码错误",
      f"code={code_of(resp)}, message={msg_of(resp)}")

st, resp = req("GET", "/auth/me", token=TOKEN)
d = data_of(resp) or {}
members = d.get("members") or []
owner_rel = members[0].get("relation") if members else None
if members:
    CAP["owner"] = members[0]["id"]
check("AUTH-05", code_of(resp) == 200 and d.get("user") and d.get("family") and owner_rel == "户主",
      f"code={code_of(resp)}, members[0].relation={owner_rel}")

st, resp = req("POST", "/auth/logout", token=TOKEN)
st2, resp2 = req("GET", "/auth/me", token=TOKEN)
check("AUTH-06", code_of(resp) == 200 and code_of(resp2) == 401,
      f"logout={code_of(resp)}, 旧令牌访问={code_of(resp2)}")

resp, TOKEN = login("demo", "demo123456")  # 重新登录
st, resp = req("PUT", "/auth/password",
               {"oldPassword": "demo123456", "newPassword": "demo1234567"}, token=TOKEN)
resp_new, t_new = login("demo", "demo1234567")
resp_old, _ = login("demo", "demo123456")
check("AUTH-07", code_of(resp) == 200 and code_of(resp_new) == 200 and code_of(resp_old) == 400,
      f"改密={code_of(resp)}, 新密码登录={code_of(resp_new)}, 旧密码登录={code_of(resp_old)}")
# 恢复原密码（保持后续用例凭据不变）
st, resp = req("PUT", "/auth/password",
               {"oldPassword": "demo1234567", "newPassword": "demo123456"}, token=t_new)
resp, TOKEN = login("demo", "demo123456")

st, resp = req("PUT", "/auth/password",
               {"oldPassword": "wrong", "newPassword": "demo1234567"}, token=TOKEN)
check("AUTH-08", code_of(resp) == 400 and msg_of(resp) == "原密码错误",
      f"code={code_of(resp)}, message={msg_of(resp)}")

st, resp = req("PUT", "/auth/password",
               {"oldPassword": "demo123456", "newPassword": "123"}, token=TOKEN)
check("AUTH-09", code_of(resp) == 400 and "密码" in msg_of(resp),
      f"code={code_of(resp)}, message={msg_of(resp)}")

# ================= 2. FAMILY / MEMBER =================
st, resp = req("GET", "/family", token=TOKEN)
check("FAMILY-01", code_of(resp) == 200 and (data_of(resp) or {}).get("name") == "演示家庭",
      f"code={code_of(resp)}, name={(data_of(resp) or {}).get('name')}")

st, resp = req("PUT", "/family", {"name": "我的小家", "description": "幸福的家"}, token=TOKEN)
st2, resp2 = req("GET", "/family", token=TOKEN)
check("FAMILY-02", code_of(resp) == 200 and (data_of(resp2) or {}).get("name") == "我的小家",
      f"code={code_of(resp)}, 改后 name={(data_of(resp2) or {}).get('name')}")

st, resp = req("POST", "/members", {"name": "李四", "relation": "配偶"}, token=TOKEN)
m1 = (data_of(resp) or {}).get("id")
check("MEMBER-01", code_of(resp) == 200 and bool(m1), f"code={code_of(resp)}, id={m1}")

st, resp = req("PUT", f"/members/{m1}",
               {"name": "李四", "relation": "配偶", "birthday": "1990-01-01"}, token=TOKEN)
check("MEMBER-02", code_of(resp) == 200, f"code={code_of(resp)}")

st, resp = req("DELETE", f"/members/{m1}", token=TOKEN)
st2, resp2 = req("GET", "/members", token=TOKEN)
still_there = any(x.get("name") == "李四" for x in (data_of(resp2) or []))
check("MEMBER-03", code_of(resp) == 200 and not still_there,
      f"delete={code_of(resp)}, 列表中仍存在={still_there}")

# ================= 3. CAT 分类 =================
st, resp = req("GET", "/categories/tree", params={"type": 2}, token=TOKEN)
tree2 = data_of(resp) or []
top_names = [x["name"] for x in tree2]
expect_tops = ["餐饮支出", "购物支出", "交通支出", "住房支出", "医疗教育", "人情往来", "休闲娱乐", "其他支出"]
canyin = find_by_name(tree2, "餐饮支出")
children_ok = canyin and len(canyin.get("children") or []) > 0
if children_ok:
    CAP["canyin"] = canyin["id"]
    # 按名称精确取"外卖"（子分类按 sortOrder 排序，不能盲目取 children[0]）
    CAP["waimai"] = (find_by_name(canyin.get("children") or [], "外卖") or {}).get("id")
CAP["jiaotong"] = (find_by_name(tree2, "交通支出") or {}).get("id")
CAP["ditie"] = (find_by_name(find_by_name(tree2, "交通支出").get("children") or [], "公共交通") or {}).get("id")
CAP["gouwu"] = (find_by_name(tree2, "购物支出") or {}).get("id")
CAP["riyong"] = (find_by_name(find_by_name(tree2, "购物支出").get("children") or [], "日用品类") or {}).get("id")
CAP["renqing"] = (find_by_name(tree2, "人情往来") or {}).get("id")
CAP["lishang"] = (find_by_name(find_by_name(tree2, "人情往来").get("children") or [], "礼尚往来") or {}).get("id")
check("CAT-01", code_of(resp) == 200 and all(t in top_names for t in expect_tops) and children_ok,
      f"顶级={top_names}, 餐饮支出有二级={children_ok}")

st, resp = req("GET", "/categories/tree", params={"type": 1}, token=TOKEN)
CAP["gongzi"] = (find_by_name(data_of(resp) or [], "工资奖金") or {}).get("id")

st, resp = req("POST", "/categories",
               {"type": 2, "parentId": 0, "name": "宠物支出", "sortOrder": 9}, token=TOKEN)
c1 = (data_of(resp) or {}).get("id")
check("CAT-02", code_of(resp) == 200 and bool(c1), f"code={code_of(resp)}, id={c1}")

st, resp = req("PUT", f"/categories/{c1}", {"type": 2, "name": "宠物日常"}, token=TOKEN)
check("CAT-03", code_of(resp) == 200, f"code={code_of(resp)}")

st, resp = req("DELETE", f"/categories/{c1}", token=TOKEN)
check("CAT-04", code_of(resp) == 200, f"code={code_of(resp)}")

st, resp = req("DELETE", f"/categories/{CAP['gongzi']}", token=TOKEN)
check("CAT-05", code_of(resp) == 400 and "内置" in msg_of(resp),
      f"code={code_of(resp)}, message={msg_of(resp)}")

st, resp = req("DELETE", f"/categories/{CAP['canyin']}", token=TOKEN)
check("CAT-06", code_of(resp) == 400 and "子分类" in msg_of(resp),
      f"code={code_of(resp)}, message={msg_of(resp)}")

st, resp = req("POST", "/categories", {"type": 2, "parentId": 0, "name": "临时分类"}, token=TOKEN)
c2 = (data_of(resp) or {}).get("id")
st, resp = req("POST", "/transactions",
               {"type": 2, "categoryId": c2, "amount": 20, "bizDate": "2026-09-04",
                "merchant": "测试商家"}, token=TOKEN)
t_tmp = (data_of(resp) or {}).get("id")
st, resp = req("DELETE", f"/categories/{c2}", token=TOKEN)
cat7_ok = code_of(resp) == 400 and ("收支记录" in msg_of(resp) or "流水" in msg_of(resp))
# 清理
req("DELETE", f"/transactions/{t_tmp}", token=TOKEN)
req("DELETE", f"/categories/{c2}", token=TOKEN)
check("CAT-07", cat7_ok, f"code={code_of(resp)}, message={msg_of(resp)}")

# ================= 4. TRANS 收支 =================
st, resp = req("POST", "/transactions",
               {"type": 1, "categoryId": CAP["gongzi"], "amount": 15000.00,
                "bizDate": "2026-09-01", "merchant": "公司"}, token=TOKEN)
t1 = (data_of(resp) or {}).get("id")
check("TRANS-01", code_of(resp) == 200 and bool(t1), f"code={code_of(resp)}, id={t1}")

st, resp = req("POST", "/transactions",
               {"type": 2, "categoryId": CAP["waimai"], "amount": 35.50,
                "bizDate": "2026-09-05", "merchant": "美团外卖", "paymentMethod": "微信"}, token=TOKEN)
t2 = (data_of(resp) or {}).get("id")
check("TRANS-02", code_of(resp) == 200 and bool(t2), f"code={code_of(resp)}, id={t2}")

bad_msgs = []
for amt in [0, -10, 1.999]:
    st, resp = req("POST", "/transactions",
                   {"type": 2, "categoryId": CAP["waimai"], "amount": amt,
                    "bizDate": "2026-09-05"}, token=TOKEN)
    bad_msgs.append(f"amount={amt}: code={code_of(resp)}, {msg_of(resp)}")
check("TRANS-03", all("金额" in m for m in bad_msgs), "; ".join(bad_msgs))

st, resp = req("GET", "/transactions", params={"page": 1, "size": 10}, token=TOKEN)
total4 = (data_of(resp) or {}).get("total")
check("TRANS-04", code_of(resp) == 200 and total4 is not None and total4 >= 2,
      f"code={code_of(resp)}, total={total4}")

st, resp = req("GET", "/transactions", params={"type": 2, "merchant": "美团"}, token=TOKEN)
recs = (data_of(resp) or {}).get("records") or []
match_ok = bool(recs) and all(r.get("type") == 2 and "美团" in (r.get("merchant") or "") for r in recs)
check("TRANS-05", code_of(resp) == 200 and match_ok,
      f"code={code_of(resp)}, 记录数={len(recs)}")

st, resp = req("PUT", f"/transactions/{t2}", {"amount": 40.00}, token=TOKEN)
st2, resp2 = req("GET", f"/transactions/{t2}", token=TOKEN)
amt6 = (data_of(resp2) or {}).get("amount")
check("TRANS-06", code_of(resp) == 200 and amt6 is not None and abs(float(amt6) - 40.0) < 0.01,
      f"code={code_of(resp)}, 更新后金额={amt6}")

st, resp = req("DELETE", f"/transactions/{t2}", token=TOKEN)
st2, resp2 = req("GET", "/transactions", params={"page": 1, "size": 10}, token=TOKEN)
total7 = (data_of(resp2) or {}).get("total")
check("TRANS-07", code_of(resp) == 200 and total7 == total4 - 1,
      f"delete={code_of(resp)}, total {total4}->{total7}")

st, resp = req("POST", "/transactions",
               {"type": 1, "categoryId": CAP["waimai"], "amount": 10, "bizDate": "2026-09-05"}, token=TOKEN)
check("TRANS-08", code_of(resp) == 400 and "不匹配" in msg_of(resp),
      f"code={code_of(resp)}, message={msg_of(resp)}")

# ================= 测试数据准备（统计/分析前置） =================
# 说明：TRANS-07 按用例删除了 9 月支出；STATS/ANALYSIS 需当月+历史流水，
# 此处按用例前置条件补录（与用例文档约定一致）
seeds = [(2, CAP["riyong"], 600, "2025-09-15", "便民超市", None, None, None)]  # 同比基准
for mth in range(1, 6):
    seeds.append((2, CAP["ditie"], 300, f"2026-{mth:02d}-10", "地铁公司", None, None, CAP["owner"]))
    seeds.append((2, CAP["riyong"], 500, f"2026-{mth:02d}-15", "便民超市", None, None, None))
for mth, wm in [(6, 100), (7, 120), (8, 150)]:
    seeds.append((2, CAP["ditie"], 300, f"2026-{mth:02d}-10", "地铁公司", None, None, CAP["owner"]))
    seeds.append((2, CAP["riyong"], 500, f"2026-{mth:02d}-15", "便民超市", None, None, None))
    seeds.append((2, CAP["waimai"], wm, f"2026-{mth:02d}-20", "美团外卖", None, None, None))
seeds += [
    (2, CAP["waimai"], 400, "2026-09-06", "美团外卖", "朝阳区", None, CAP["owner"]),
    (2, CAP["ditie"], 300, "2026-09-07", "地铁公司", "海淀区", None, CAP["owner"]),
    (2, CAP["lishang"], 300, "2026-09-08", "王小五", "朝阳区", "礼尚往来", None),
]
seed_fail = 0
for tp, cid, amt, dt, mer, reg, tags, mid in seeds:
    body = {"type": tp, "categoryId": cid, "amount": amt, "bizDate": dt, "merchant": mer}
    if reg:
        body["region"] = reg
    if tags:
        body["tags"] = tags
    if mid:
        body["memberId"] = mid
    st, resp = req("POST", "/transactions", body, token=TOKEN)
    if code_of(resp) != 200:
        seed_fail += 1
print(f"[数据准备] 补录 {len(seeds)} 笔历史/当月流水, 失败 {seed_fail}")

# ================= 5. STATS 统计 =================
st, resp = req("GET", "/stats/overview", params={"year": 2026, "month": 9}, token=TOKEN)
ov = data_of(resp) or {}
inc = float(ov.get("income") or 0)
exp = float(ov.get("expense") or 0)
bal = float(ov.get("balance") or 0)
bal_ok = abs(inc - 15000) < 0.01 and exp >= 35.50 and abs(bal - (inc - exp)) < 0.01
check("STATS-01", code_of(resp) == 200 and bal_ok,
      f"income={ov.get('income')}, expense={ov.get('expense')}, balance={ov.get('balance')}")

st, resp = req("GET", "/stats/trend", params={"months": 12}, token=TOKEN)
trend = data_of(resp) or []
sept_pt = next((p for p in trend if p.get("month") == "2026-09"), None)
check("STATS-02", code_of(resp) == 200 and len(trend) == 12 and sept_pt is not None
      and abs(float(sept_pt.get("income") or 0) - 15000) < 0.01
      and abs(float(sept_pt.get("expense") or 0) - 1000) < 0.01,
      f"点数={len(trend)}, 2026-09 点={sept_pt}")

st, resp = req("GET", "/stats/category", params={"year": 2026, "month": 9, "type": 2}, token=TOKEN)
cat_stats = data_of(resp) or []
wm_stat = find_by_name(cat_stats, "外卖")
check("STATS-03", code_of(resp) == 200 and wm_stat is not None
      and abs(float(wm_stat.get("total") or 0) - 400) < 0.01,
      f"外卖 total={wm_stat.get('total') if wm_stat else None}, 分类数={len(cat_stats)}")

st1, r1 = req("GET", "/stats/member", params={"year": 2026, "month": 9, "type": 2}, token=TOKEN)
st2, r2 = req("GET", "/stats/merchant", params={"year": 2026, "month": 9, "type": 2}, token=TOKEN)
st3, r3 = req("GET", "/stats/region", params={"year": 2026, "month": 9, "type": 2}, token=TOKEN)
st4, r4 = req("GET", "/stats/tags", params={"year": 2026, "month": 9, "type": 2}, token=TOKEN)
owner_stat = find_by_name(data_of(r1) or [], "演示员")
mt_stat = find_by_name(data_of(r2) or [], "美团外卖")
rg_stat = find_by_name(data_of(r3) or [], "朝阳区")
tg_stat = find_by_name(data_of(r4) or [], "礼尚往来")
check("STATS-04",
      code_of(r1) == 200 and code_of(r2) == 200 and code_of(r3) == 200 and code_of(r4) == 200
      and owner_stat and abs(float(owner_stat.get("total") or 0) - 700) < 0.01
      and mt_stat and abs(float(mt_stat.get("total") or 0) - 400) < 0.01
      and rg_stat and abs(float(rg_stat.get("total") or 0) - 700) < 0.01
      and tg_stat and abs(float(tg_stat.get("total") or 0) - 300) < 0.01,
      f"member={code_of(r1)}/{owner_stat}, merchant={code_of(r2)}/{mt_stat}, region={code_of(r3)}/{rg_stat}, tags={code_of(r4)}/{tg_stat}")

# ================= 6. ANALYSIS 分析 =================
st, resp = req("GET", "/analysis/compare", params={"month": "2026-09"}, token=TOKEN)
cmp = data_of(resp) or {}
exp = cmp.get("expense") or {}
mom_ok = exp.get("momPct") is not None and abs(float(exp.get("momPct")) - 5.3) < 0.2
yoy_ok = exp.get("yoyPct") is not None and abs(float(exp.get("yoyPct")) - 66.7) < 0.2
check("ANALYSIS-01", code_of(resp) == 200 and mom_ok and yoy_ok,
      f"expense: current={exp.get('current')}, mom={exp.get('mom')}, momPct={exp.get('momPct')}, yoyPct={exp.get('yoyPct')}")

st, resp = req("GET", "/analysis/anomalies", params={"month": "2026-09"}, token=TOKEN)
anoms = data_of(resp) or []
r2 = next((a for a in anoms if a.get("ruleCode") == "R2"), None)
r2_ok = r2 is not None and r2.get("level") == "warning" and r2.get("drillCategoryId") == CAP["waimai"]
check("ANALYSIS-02", code_of(resp) == 200 and r2_ok,
      f"规则数={len(anoms)}, rules={[a.get('ruleCode') for a in anoms]}, R2={ {k: r2.get(k) for k in ('level','drillCategoryId')} if r2 else None}")

st, resp = req("GET", "/analysis/report", params={"month": "2026-09"}, token=TOKEN)
txt = (data_of(resp) or {}).get("text") or ""
sec_ok = all(s in txt for s in ["一、本月概况", "二、发现的问题", "四、建议"])
check("ANALYSIS-03", code_of(resp) == 200 and len(txt) > 200 and sec_ok,
      f"text 长度={len(txt)}, 包含四段结构={sec_ok}")

# ================= 7. ASSET / LOAN =================
st, resp = req("POST", "/assets",
               {"assetType": "房产", "name": "望京两居室", "value": 5200000,
                "purchaseDate": "2020-06-15"}, token=TOKEN)
a1 = (data_of(resp) or {}).get("id")
check("ASSET-01", code_of(resp) == 200 and bool(a1), f"code={code_of(resp)}, id={a1}")

st, resp = req("POST", "/assets",
               {"assetType": "股票", "name": "某股票", "value": 10000}, token=TOKEN)
check("ASSET-02", code_of(resp) == 400 and "房产" in msg_of(resp),
      f"code={code_of(resp)}, message={msg_of(resp)}")

st, resp = req("GET", "/assets/summary", token=TOKEN)
sm = data_of(resp) or {}
by_type = {x.get("name"): float(x.get("total") or 0) for x in (sm.get("byType") or [])}
check("ASSET-03", code_of(resp) == 200 and abs(float(sm.get("totalAssets") or 0) - 5200000) < 0.01
      and abs(by_type.get("房产", 0) - 5200000) < 0.01,
      f"totalAssets={sm.get('totalAssets')}, byType={by_type}")

st, resp = req("PUT", f"/assets/{a1}", {"value": 5000000}, token=TOKEN)
st2, resp2 = req("DELETE", f"/assets/{a1}", token=TOKEN)
st3, resp3 = req("GET", "/assets", token=TOKEN)
left = [x for x in (data_of(resp3) or []) if x.get("name") == "望京两居室"]
check("ASSET-04", code_of(resp) == 200 and code_of(resp2) == 200 and not left,
      f"put={code_of(resp)}, delete={code_of(resp2)}, 残留={len(left)}")

st, resp = req("POST", "/loans",
               {"name": "房贷", "principal": 1000000, "annualRate": 0.038,
                "termMonths": 360, "repaymentType": "equal_installment"}, token=TOKEN)
l1 = (data_of(resp) or {}).get("id")
mp = (data_of(resp) or {}).get("monthlyPayment")
check("LOAN-01", code_of(resp) == 200 and bool(l1) and mp is not None
      and abs(float(mp) - 4659.57) < 0.1,
      f"code={code_of(resp)}, id={l1}, monthlyPayment={mp}")

st, resp = req("GET", f"/loans/{l1}/plan", token=TOKEN)
plan = data_of(resp) or {}
items = plan.get("items") or []
last_rem = items[-1].get("remainingPrincipal") if items else None
tp = plan.get("totalPayment")
# 末期按分调整结清，故 totalPayment 与 monthlyPayment×360 存在 ≤10 元的累计四舍五入差异
check("LOAN-02", code_of(resp) == 200 and len(items) == 360
      and last_rem is not None and abs(float(last_rem)) < 0.01 and tp is not None
      and abs(float(tp) - float(mp) * 360) < 10,
      f"期数={len(items)}, 末期剩余={last_rem}, totalPayment={tp}, monthly×360={float(mp)*360:.2f}")

st, resp = req("POST", "/loans",
               {"name": "车贷", "principal": 200000, "annualRate": 0.045,
                "termMonths": 60, "repaymentType": "equal_principal"}, token=TOKEN)
l2 = (data_of(resp) or {}).get("id")
st, resp = req("GET", f"/loans/{l2}/plan", token=TOKEN)
items3 = (data_of(resp) or {}).get("items") or []
p0 = items3[0].get("principal") if items3 else None
i0 = items3[0].get("interest") if items3 else None
i1 = items3[1].get("interest") if len(items3) > 1 else None
last3 = items3[-1].get("remainingPrincipal") if items3 else None
check("LOAN-03", code_of(resp) == 200 and len(items3) == 60
      and abs(float(p0 or 0) - 3333.33) < 0.05 and float(i0) > float(i1)
      and last3 is not None and abs(float(last3)) < 0.01,
      f"首期本金={p0}, 首期利息={i0}, 二期利息={i1}, 末期剩余={last3}")

st, resp = req("DELETE", f"/loans/{l1}", token=TOKEN)
req("DELETE", f"/loans/{l2}", token=TOKEN)  # 清理
check("LOAN-04", code_of(resp) == 200, f"code={code_of(resp)}")

# ================= 8. BUDGET 预算 =================
st, resp = req("POST", "/budgets",
               {"categoryId": None, "budgetMonth": "2026-09", "amount": 12000}, token=TOKEN)
b1 = (data_of(resp) or {}).get("id")
check("BUDGET-01", code_of(resp) == 200 and bool(b1), f"code={code_of(resp)}, id={b1}")

st, resp = req("POST", "/budgets",
               {"categoryId": CAP["canyin"], "budgetMonth": "2026-09", "amount": 2000}, token=TOKEN)
b2 = (data_of(resp) or {}).get("id")
check("BUDGET-02", code_of(resp) == 200 and bool(b2), f"code={code_of(resp)}, id={b2}")

st, resp = req("POST", "/budgets",
               {"categoryId": None, "budgetMonth": "2026-09", "amount": 12000}, token=TOKEN)
check("BUDGET-03", code_of(resp) == 400 and "已存在" in msg_of(resp),
      f"code={code_of(resp)}, message={msg_of(resp)}")

st, resp = req("GET", "/budgets/execution", params={"month": "2026-09"}, token=TOKEN)
execs = data_of(resp) or []
total_exec = next((x for x in execs if x.get("categoryId") is None), None)
canyin_exec = next((x for x in execs if x.get("categoryId") == CAP["canyin"]), None)
rate_ok = (total_exec and abs(float(total_exec.get("rate")) - 1000 / 12000 * 100) < 0.1
           and total_exec.get("overrun") is False
           and canyin_exec and abs(float(canyin_exec.get("rate")) - 400 / 2000 * 100) < 0.1
           and canyin_exec.get("overrun") is False)
check("BUDGET-04", code_of(resp) == 200 and rate_ok,
      f"总预算 rate={total_exec.get('rate') if total_exec else None}, overrun={total_exec.get('overrun') if total_exec else None}; "
      f"餐饮 rate={canyin_exec.get('rate') if canyin_exec else None}, overrun={canyin_exec.get('overrun') if canyin_exec else None}")

# ================= 9. SEC 安全与健壮性 =================
st, resp = req("POST", "/auth/register",
               {"username": "demo2", "password": "demo123456",
                "nickname": "测试员B", "familyName": "演示家庭B"})
TOKEN_B = (data_of(resp) or {}).get("token")
# SEC-02: 临时改库把 demo2 降为 MEMBER（用例文档允许的方式）
db("UPDATE user SET role='MEMBER' WHERE username='demo2'")
resp, TOKEN_B = login("demo2", "demo123456")
st, resp = req("POST", "/members", {"name": "测试成员"}, token=TOKEN_B)
sec2_ok = code_of(resp) == 403 and "管理员" in msg_of(resp)
db("UPDATE user SET role='ADMIN' WHERE username='demo2'")  # 恢复
check("SEC-02", TOKEN_B is not None and sec2_ok,
      f"code={code_of(resp)}, message={msg_of(resp)}（临时改库降权验证后已恢复）")

# SEC-03 参数校验
st1, r1 = req("POST", "/auth/register",
              {"username": "ab", "password": "demo123456", "familyName": "测试"})
st2, r2 = req("POST", "/transactions",
              {"type": 2, "categoryId": CAP["waimai"], "bizDate": "2026-09-05"}, token=TOKEN)
st3, r3 = req("POST", "/members", {"name": "长" * 51}, token=TOKEN)
sec3_ok = (code_of(r1) == 400 and "用户名" in msg_of(r1)
           and code_of(r2) == 400 and "金额" in msg_of(r2)
           and code_of(r3) == 400 and "最长" in msg_of(r3))
check("SEC-03", sec3_ok,
      f"用户名过短={code_of(r1)}/{msg_of(r1)}; 缺金额={code_of(r2)}/{msg_of(r2)}; 姓名超长={code_of(r3)}/{msg_of(r3)}")

# SEC-04 跨家庭数据隔离
resp, TOKEN_B = login("demo2", "demo123456")
st, resp = req("GET", "/categories/tree", params={"type": 2}, token=TOKEN_B)
tree_b = data_of(resp) or []
wm_b = (find_by_name(find_by_name(tree_b, "餐饮支出").get("children") or [], "外卖") or {}).get("id")
st, resp = req("POST", "/transactions",
               {"type": 2, "categoryId": wm_b, "amount": 100, "bizDate": "2026-09-05",
                "merchant": "B家商家"}, token=TOKEN_B)
b_tx = (data_of(resp) or {}).get("id")
st, resp = req("POST", "/assets",
               {"assetType": "存款", "name": "B家存款", "value": 100000}, token=TOKEN_B)
b_asset = (data_of(resp) or {}).get("id")

st1, r1 = req("GET", f"/transactions/{b_tx}", token=TOKEN)
st2, r2 = req("GET", f"/assets/{b_asset}", token=TOKEN)
st3, r3 = req("DELETE", f"/transactions/{b_tx}", token=TOKEN)
st4, r4 = req("GET", "/transactions", params={"page": 1, "size": 100}, token=TOKEN_B)
b_records = (data_of(r4) or {}).get("records") or []
leak = any("公司" in (x.get("merchant") or "") or "美团" in (x.get("merchant") or "")
           or "地铁" in (x.get("merchant") or "") for x in b_records)
st5, r5 = req("GET", "/family", token=TOKEN_B)
check("SEC-04",
      (code_of(r1) in (400, 404) and "不存在" in msg_of(r1))
      and (code_of(r2) in (400, 404) and "不存在" in msg_of(r2))
      and (code_of(r3) in (400, 404) and "不存在" in msg_of(r3))
      and not leak
      and (data_of(r5) or {}).get("name") == "演示家庭B",
      f"A访问B流水={code_of(r1)}/{msg_of(r1)}; A访问B资产={code_of(r2)}/{msg_of(r2)}; "
      f"A删B流水={code_of(r3)}/{msg_of(r3)}; B列表泄露A数据={leak}; B家庭名={(data_of(r5) or {}).get('name')}")

# ================= 汇总 =================
passed = sum(1 for _, ok, _ in results if ok)
total = len(results)
print("\n" + "=" * 60)
print(f"通过 {passed} / {total}")
for cid, ok, note in results:
    if not ok:
        print(f"  FAIL: {cid} — {note}")
with open("/tmp/it_results.json", "w", encoding="utf-8") as f:
    json.dump([{"case": c, "ok": ok, "note": n} for c, ok, n in results], f,
              ensure_ascii=False, indent=2)
sys.exit(0 if passed == total else 1)
