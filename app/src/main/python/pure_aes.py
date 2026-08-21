# -*- coding: utf-8 -*-
"""Pure-Python AES (CBC / PKCS7)，支持 128/192/256 位密钥。

替代 pycryptodome（C 扩展，Android 端 Chaquopy 构建不便），
仅实现本脚本用到的子集：AES-CBC 加密 + PKCS7 padding。
接口与 pycryptodome 兼容：AES.new(key, AES.MODE_CBC, iv) / cipher.encrypt(data) / pad()。
已通过 FIPS-197 / NIST SP800-38A 标准向量验证。
"""


def _gmul(a, b):
    """GF(2^8) 乘法（模不可约多项式 0x11B）。"""
    p = 0
    for _ in range(8):
        if b & 1:
            p ^= a
        carry = a & 0x80
        a = (a << 1) & 0xFF
        if carry:
            a ^= 0x1B
        b >>= 1
    return p


def _rotl(v, n):
    """字节循环左移。"""
    return ((v << n) & 0xFF) | (v >> (8 - n))


def _build_sbox():
    """由 GF(2^8) 逆元 + 仿射变换构造 S-box（不硬编码查表，避免抄错）。"""
    sbox = [0] * 256
    for x in range(256):
        if x == 0:
            inv = 0
        else:
            # x^254 = x^-1 (Fermat，GF(2^8) 乘法)
            r, e, base = 1, 254, x
            while e:
                if e & 1:
                    r = _gmul(r, base)
                base = _gmul(base, base)
                e >>= 1
            inv = r
        s = inv ^ _rotl(inv, 1) ^ _rotl(inv, 2) ^ _rotl(inv, 3) ^ _rotl(inv, 4) ^ 0x63
        sbox[x] = s & 0xFF
    return sbox


_SBOX = _build_sbox()


def _expand_key(key):
    """密钥扩展，返回 (轮密钥字节列表, 轮数)。支持 16/24/32 字节 key（AES-128/192/256）。"""
    nk = len(key) // 4
    nr = nk + 6  # 10/12/14 轮
    w = [list(key[i * 4:(i + 1) * 4]) for i in range(nk)]
    rcon = 1
    i = nk
    while len(w) < 4 * (nr + 1):
        t = list(w[-1])
        if i % nk == 0:
            t = t[1:] + t[:1]            # RotWord
            t = [_SBOX[b] for b in t]    # SubWord
            t[0] ^= rcon                 # Rcon
            rcon = _gmul(rcon, 2)
        elif nk > 6 and i % nk == 4:
            t = [_SBOX[b] for b in t]    # AES-256 每 8 个字额外 SubWord
        w.append([w[i - nk][j] ^ t[j] for j in range(4)])
        i += 1
    return [b for word in w for b in word], nr


def _mix_columns(st):
    out = [0] * 16
    for c in range(4):
        i = c * 4
        a0, a1, a2, a3 = st[i], st[i + 1], st[i + 2], st[i + 3]
        out[i] = _gmul(a0, 2) ^ _gmul(a1, 3) ^ a2 ^ a3
        out[i + 1] = a0 ^ _gmul(a1, 2) ^ _gmul(a2, 3) ^ a3
        out[i + 2] = a0 ^ a1 ^ _gmul(a2, 2) ^ _gmul(a3, 3)
        out[i + 3] = _gmul(a0, 3) ^ a1 ^ a2 ^ _gmul(a3, 2)
    return out


def _encrypt_block(block, rk, nr):
    st = [block[i] ^ rk[i] for i in range(16)]
    for rnd in range(1, nr + 1):
        st = [_SBOX[b] for b in st]                        # SubBytes
        st = [st[0], st[5], st[10], st[15],                # ShiftRows（列主序）
              st[4], st[9], st[14], st[3],
              st[8], st[13], st[2], st[7],
              st[12], st[1], st[6], st[11]]
        if rnd < nr:
            st = _mix_columns(st)                          # MixColumns
        off = rnd * 16
        st = [st[i] ^ rk[off + i] for i in range(16)]      # AddRoundKey
    return bytes(st)


class AES:
    block_size = 16
    MODE_CBC = 2

    @staticmethod
    def new(key, mode, iv=None):
        """pycryptodome 兼容入口：AES.new(key, mode, iv) -> 实例。"""
        return AES(key, mode, iv)

    def __init__(self, key, mode, iv=None):
        if mode != AES.MODE_CBC:
            raise ValueError("pure_aes 仅支持 CBC 模式")
        if len(key) not in (16, 24, 32):
            raise ValueError("pure_aes 仅支持 128/192/256 位密钥")
        self._rk, self._nr = _expand_key(key)
        self._iv = bytes(iv) if iv is not None else None

    def encrypt(self, data):
        prev = self._iv
        out = bytearray()
        for i in range(0, len(data), 16):
            block = bytes(a ^ b for a, b in zip(data[i:i + 16], prev))
            enc = _encrypt_block(block, self._rk, self._nr)
            out += enc
            prev = enc
        return bytes(out)


def pad(data, block_size):
    """PKCS7 padding。"""
    n = block_size - (len(data) % block_size)
    return data + bytes([n]) * n
