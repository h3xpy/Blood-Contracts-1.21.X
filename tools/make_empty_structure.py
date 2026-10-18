"""Writes an empty GameTest template (an air box).

Usage: python make_empty_structure.py <out.nbt> [size_x size_y size_z]   (default 8 8 8)
"""
import gzip, struct, sys

def name(s):
    b = s.encode('utf-8'); return struct.pack('>H', len(b)) + b

def tag(t, n, payload):
    return bytes([t]) + name(n) + payload

def int_(v): return struct.pack('>i', v)

def list_(elem_type, items):
    return bytes([elem_type]) + int_(len(items)) + b''.join(items)

def compound(entries):
    return b''.join(entries) + b'\x00'

size = [int(v) for v in sys.argv[2:5]] if len(sys.argv) >= 5 else [8, 8, 8]
root = compound([
    tag(3, 'DataVersion', int_(3955)),  # Minecraft 1.21.1
    tag(9, 'size', list_(3, [int_(v) for v in size])),
    tag(9, 'palette', list_(10, [compound([tag(8, 'Name', name('minecraft:air'))])])),
    tag(9, 'blocks', list_(10, [])),
    tag(9, 'entities', list_(10, [])),
])
with gzip.open(sys.argv[1], 'wb') as f:
    f.write(tag(10, '', root))
