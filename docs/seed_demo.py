#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""管家婆─家庭收支管理系统 演示数据种子脚本

作用：注册演示账号 demo/demo123456（演示家庭）并灌入演示数据：
  - 73 笔流水（2025-09 ~ 2026-09，覆盖收入/支出、成员、商家、片区、标签）
  - 3 名成员（爸爸-户主 / 妈妈-配偶 / 儿子-子女）
  - 2026-09 预算 2 条（外卖 200 → 超支触发 R3；家庭总预算 6000）
  - 资产 3 条（房产 300 万 / 存款 20 万 / 汽车 15 万）
  - 贷款 2 条（房贷等额本息 / 车贷等额本金，可查看还款计划）
  - 智能分析规则 R1~R7 全部触发（供答辩演示）

前置条件：后端 :8080 已启动；MySQL :3307 可访问（仅重跑清理时用到）。
用法：python3 docs/seed_demo.py
幂等：demo 已存在且密码正确时，先清空其流水/预算/资产/贷款/非户主成员再重灌；
      密码错误则提示（改回 demo123456 后重跑，或重跑 sql/init.sql 从零开始）。
"""
import json
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = "http://localhost:8080/api"
USERNAME, PASSWORD = "demo", "demo123456"


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
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read().decode("utf-8"))
        except Exception:
            return {"code": e.code, "message": str(e)}


def db(sql):
    """重跑清理用：直连 MySQL 按 family_id 清空演示数据"""
    out = subprocess.run(
        ["mysql", "-h", "127.0.0.1", "-P", "3307", "-uroot", "-proot",
         "family_finance", "-N", "-e", sql],
        capture_output=True, text=True)
    if out.returncode != 0:
        raise RuntimeError("DB 清理失败（重跑场景需要 MySQL :3307 可用）: " + out.stderr)
    return out.stdout.strip()


def code_of(resp):
    return resp.get("code") if isinstance(resp, dict) else None


def msg_of(resp):
    return resp.get("message", "") if isinstance(resp, dict) else ""


def data_of(resp):
    return resp.get("data") if isinstance(resp, dict) else None


def ok(resp):
    return code_of(resp) == 200


def find_by_name(lst, name):
    for x in lst or []:
        if x.get("name") == name:
            return x
    return None


def main():
    # ---------- 1. 注册 / 登录 ----------
    resp = req("POST", "/auth/register",
               {"username": USERNAME, "password": PASSWORD,
                "nickname": "演示员", "familyName": "演示家庭"})
    if not ok(resp):
        if "已存在" not in msg_of(resp):
            raise SystemExit(f"注册失败: code={code_of(resp)}, message={msg_of(resp)}")
        resp = req("POST", "/auth/login", {"username": USERNAME, "password": PASSWORD})
        if not ok(resp):
            raise SystemExit(
                "demo 账号已存在但密码不是 demo123456。请在系统「个人中心」改回密码后重跑本脚本，\n"
                "或执行 sql/init.sql 重置数据库后再重跑（注意：init.sql 会清空全部数据）。")
        token = data_of(resp).get("token")
        print("[1/8] demo 已存在，登录成功 → 清空旧演示数据")
        me = data_of(req("GET", "/auth/me", token=token)) or {}
        fid = (me.get("family") or {}).get("id")
        db(f"DELETE FROM transaction WHERE family_id={fid}")
        db(f"DELETE FROM budget WHERE family_id={fid}")
        db(f"DELETE FROM asset WHERE family_id={fid}")
        db(f"DELETE FROM loan WHERE family_id={fid}")
        db(f"DELETE FROM family_member WHERE family_id={fid} AND relation<>'户主'")
    else:
        token = data_of(resp).get("token")
        print("[1/8] 注册 demo/demo123456（演示家庭）成功")

    # ---------- 2. 家庭上下文 ----------
    me = data_of(req("GET", "/auth/me", token=token)) or {}
    members = me.get("members") or []
    owner = next((m for m in members if m.get("relation") == "户主"), None)
    if not owner:
        raise SystemExit("未找到户主成员，注册异常，请重置数据库后重试")
    print(f"[2/8] 家庭: {(me.get('family') or {}).get('name')}, 户主 id={owner['id']}")

    # ---------- 3. 成员：户主改名爸爸，新增妈妈/儿子 ----------
    resp = req("PUT", f"/members/{owner['id']}",
               {"name": "爸爸", "relation": "户主"}, token=token)
    assert ok(resp), f"户主改名失败: {msg_of(resp)}"
    resp = req("POST", "/members", {"name": "妈妈", "relation": "配偶"}, token=token)
    assert ok(resp), f"添加妈妈失败: {msg_of(resp)}"
    mom_id = data_of(resp).get("id")
    resp = req("POST", "/members", {"name": "儿子", "relation": "子女"}, token=token)
    assert ok(resp), f"添加儿子失败: {msg_of(resp)}"
    print(f"[3/8] 成员: 爸爸(户主) / 妈妈(id={mom_id}) / 儿子")

    # ---------- 4. 分类树 → id 映射 ----------
    def cat_map(tp):
        m = {}
        for top in data_of(req("GET", "/categories/tree", params={"type": tp}, token=token)) or []:
            m[top["name"]] = top["id"]
            for c in top.get("children") or []:
                m[c["name"]] = c["id"]
                for g in c.get("children") or []:
                    m[g["name"]] = g["id"]
        return m

    C1, C2 = cat_map(1), cat_map(2)
    need = ["工资奖金", "投资收益", "礼金红包"] + \
           ["在家做饭", "外卖", "日用品类", "服饰类", "电子产品类",
            "公共交通", "车辆用度", "物业水电", "教育", "医疗", "礼尚往来"]
    missing = [n for n in need if n not in C1 and n not in C2]
    assert not missing, f"内置分类缺失: {missing}"
    print("[4/8] 分类映射就绪（收入 {} 类 / 支出 {} 类）".format(len(C1), len(C2)))

    # ---------- 5. 灌 73 笔流水 ----------
    # (type, 分类, 金额, 日期, 商家, 片区, 标签, 成员)
    tx = []
    # 2025-09 同比基准
    tx += [(2, "公共交通", 250, "2025-09-10", "地铁公司", None, None, owner["id"]),
           (2, "在家做饭", 500, "2025-09-15", "便民超市", None, None, None),
           (1, "工资奖金", 12000, "2025-09-01", "公司", None, None, owner["id"]),
           (2, "日用品类", 600, "2025-09-15", "便民超市", None, None, None)]
    # 2026-01 ~ 08 每月常规
    for mth in range(1, 9):
        mm = f"{mth:02d}"
        tx += [(2, "公共交通", 300, f"2026-{mm}-10", "地铁公司", None, None, owner["id"]),
               (2, "物业水电", 260, f"2026-{mm}-12", "物业公司", None, None, None),
               (2, "在家做饭", 600, f"2026-{mm}-15", "便民超市", None, None, None),
               (2, "日用品类", 500, f"2026-{mm}-15", "便民超市", None, None, None),
               (1, "工资奖金", 15000, f"2026-{mm}-01", "公司", None, None, owner["id"]),
               (1, "投资收益", 500, f"2026-{mm}-20", "理财平台", None, None, mom_id)]
    # 特殊事件
    tx += [(1, "礼金红包", 2000, "2026-01-30", "春节红包", None, "春节", None),
           (2, "礼尚往来", 500, "2026-02-08", "王阿姨", None, "拜年", None),
           (2, "教育", 800, "2026-02-20", "新东方培训", None, None, None),
           (2, "服饰类", 450, "2026-03-15", "优衣库", None, None, mom_id),
           (2, "医疗", 300, "2026-05-18", "市人民医院", None, None, None),
           (2, "外卖", 100, "2026-06-20", "美团外卖", None, None, None),
           (2, "外卖", 120, "2026-07-20", "美团外卖", None, None, None),
           (2, "外卖", 150, "2026-08-20", "美团外卖", None, None, None),
           (2, "车辆用度", 400, "2026-08-18", "中石化加油站", None, None, owner["id"])]
    # 2026-09 当月
    tx += [(1, "工资奖金", 15000, "2026-09-01", "公司", None, None, owner["id"]),
           (2, "教育", 800, "2026-09-02", "新东方培训", None, None, None),
           (2, "公共交通", 300, "2026-09-03", "地铁公司", None, None, owner["id"]),
           (2, "物业水电", 268, "2026-09-04", "物业公司", None, None, None),
           (2, "外卖", 400, "2026-09-06", "美团外卖", "朝阳区", None, owner["id"]),
           (2, "礼尚往来", 300, "2026-09-08", "王小五", "朝阳区", "礼尚往来,中秋", None),
           (2, "在家做饭", 600, "2026-09-15", "便民超市", None, None, None),
           (2, "日用品类", 500, "2026-09-15", "便民超市", None, None, None),
           (2, "车辆用度", 400, "2026-09-18", "中石化加油站", None, None, owner["id"]),
           (1, "投资收益", 500, "2026-09-20", "理财平台", None, None, mom_id),
           (2, "电子产品类", 1299, "2026-09-21", "京东商城", None, None, None),
           (2, "外卖", 150, "2026-09-25", "饿了么", "海淀区", None, None)]
    fail = 0
    for tp, cname, amt, dt, mer, reg, tags, mid in tx:
        body = {"type": tp, "categoryId": (C1 if tp == 1 else C2)[cname],
                "amount": amt, "bizDate": dt, "merchant": mer}
        if reg:
            body["region"] = reg
        if tags:
            body["tags"] = tags
        if mid:
            body["memberId"] = mid
        resp = req("POST", "/transactions", body, token=token)
        if not ok(resp):
            fail += 1
            print(f"  流水失败: {cname} {dt} → {msg_of(resp)}")
    assert fail == 0, f"{fail} 笔流水灌入失败"
    resp = req("GET", "/transactions", params={"page": 1, "size": 1}, token=token)
    print(f"[5/8] 流水 {len(tx)} 笔灌入完成（库内合计 {data_of(resp).get('total')} 笔）")

    # ---------- 6. 预算（2026-09） ----------
    resp = req("POST", "/budgets",
               {"categoryId": C2["外卖"], "budgetMonth": "2026-09", "amount": 200}, token=token)
    assert ok(resp), f"外卖预算失败: {msg_of(resp)}"
    resp = req("POST", "/budgets",
               {"categoryId": None, "budgetMonth": "2026-09", "amount": 6000}, token=token)
    assert ok(resp), f"总预算失败: {msg_of(resp)}"
    print("[6/8] 预算: 外卖 200（将超支→R3）/ 家庭总预算 6000（约 84%）")

    # ---------- 7. 资产 ----------
    for at, name, val, pd in [("房产", "望京两居室", 3000000, "2020-06-15"),
                              ("存款", "银行定期存款", 200000, "2024-01-01"),
                              ("汽车", "家用轿车", 150000, "2022-05-20")]:
        resp = req("POST", "/assets",
                   {"assetType": at, "name": name, "value": val, "purchaseDate": pd}, token=token)
        assert ok(resp), f"资产 {name} 失败: {msg_of(resp)}"
    print("[7/8] 资产: 房产 300 万 / 存款 20 万 / 汽车 15 万")

    # ---------- 8. 贷款 ----------
    for name, p, r, n, rt, sd, lender in [
            ("房贷", 1000000, 0.038, 360, "equal_installment", "2021-01-01", "建设银行"),
            ("车贷", 120000, 0.032, 36, "equal_principal", "2023-06-01", "招商银行")]:
        resp = req("POST", "/loans",
                   {"name": name, "principal": p, "annualRate": r, "termMonths": n,
                    "repaymentType": rt, "startDate": sd, "lender": lender}, token=token)
        assert ok(resp), f"贷款 {name} 失败: {msg_of(resp)}"
    print("[8/8] 贷款: 房贷(等额本息·建设银行) / 车贷(等额本金·招商银行)")

    # ---------- 验证 ----------
    resp = req("POST", "/auth/login", {"username": USERNAME, "password": PASSWORD})
    assert ok(resp), "最终登录验证失败"
    # Sa-Token 默认不允许并发登录：本次登录会把 step1 的会话顶掉，
    # 必须用新 token 请求 anomalies（旧 token 已 401）
    token = data_of(resp).get("token")
    anoms = data_of(req("GET", "/analysis/anomalies",
                        params={"month": "2026-09"}, token=token)) or []
    rules = sorted({a.get("ruleCode") for a in anoms})
    print("\n验证通过：demo / demo123456 可登录（演示家庭）")
    print(f"智能分析触发规则: {rules}")
    print("浏览器打开 http://localhost:5173 即可演示。")


if __name__ == "__main__":
    main()
