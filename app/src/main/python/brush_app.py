# -*- coding: utf-8 -*-
"""App 入口：把 ewt_brush_v2 包装为可被 Kotlin 调用的接口，日志实时回写文件供 UI 显示。"""
import asyncio
import logging
import os
import sys


class _Tee:
    def __init__(self, real, fh):
        self._real = real
        self._fh = fh

    def write(self, s):
        try:
            self._real.write(s)
        except Exception:
            pass
        try:
            self._fh.write(s)
            self._fh.flush()
        except Exception:
            pass
        return len(s)

    def flush(self):
        try:
            self._real.flush()
        except Exception:
            pass
        try:
            self._fh.flush()
        except Exception:
            pass


def _prepare(log_path):
    try:
        fh = open(log_path, "a", encoding="utf-8", buffering=1)
    except Exception:
        fh = None
    if fh:
        sys.stdout = _Tee(sys.__stdout__, fh)
        sys.stderr = _Tee(sys.__stderr__, fh)
    for h in list(logging.root.handlers):
        logging.root.removeHandler(h)
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    token_file = os.path.join(os.path.dirname(log_path) or ".", "token.txt")
    os.environ["EWT_TOKEN_FILE"] = token_file
    import ewt_brush_v2 as b
    return b


def do_login(log_path, account, password):
    b = _prepare(log_path)
    try:
        # login 是 async 函数，必须用 asyncio.run 包裹
        token = asyncio.run(b.login(account, password))
        print("✓ 登录成功: %s…%s" % (token[:16], token[-8:]))
        return 0
    except Exception as e:
        print("✗ 登录失败: %s" % e)
        return 1


def run_brush(log_path, account, password, hw_filter="", concurrency=6, qps=150.0, dry_run=False, burst_size=8):
    b = _prepare(log_path)
    try:
        token = b.load_token_file()
        if not token:
            print("未找到已保存 token，正在登录…")
            # login 是 async 函数，必须用 asyncio.run 包裹
            token = asyncio.run(b.login(account, password))
            print("✓ 登录成功: %s…%s" % (token[:16], token[-8:]))
        else:
            print("✓ 使用已保存 token: %s…%s" % (token[:16], token[-8:]))
        code = asyncio.run(b.run_brush_all(
            token, account, password,
            hw_filter=hw_filter or None,
            concurrency=int(concurrency),
            qps=float(qps),
            dry_run=bool(dry_run),
            burst_size=int(burst_size),
        ))
        return int(code)
    except Exception:
        import traceback
        traceback.print_exc()
        return 1
