"""Fail builds that silently discover zero or skip critical tests."""
from pathlib import Path
import xml.etree.ElementTree as ET
files=list((Path(__file__).resolve().parents[1]/'app/build/test-results/test').glob('TEST-*.xml'))
counts={k:0 for k in ('tests','failures','errors','skipped')}
for path in files:
    root=ET.parse(path).getroot()
    for key in counts: counts[key]+=int(root.attrib.get(key,0))
print(counts)
assert counts['tests']>=34, 'Expected business and migration integration tests'
assert counts['failures']==counts['errors']==counts['skipped']==0, 'Tests failed or skipped'
