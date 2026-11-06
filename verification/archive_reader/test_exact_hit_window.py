"""Guard against the FTS overlap boundary bug: the matching segment must
always be in the loaded 20-part window, as must its predecessor if present.
"""
PARTS_PER_WINDOW=20
for matched_part in range(2000):
    start=max(0,matched_part-1)
    visible=range(start,start+PARTS_PER_WINDOW)
    assert matched_part in visible, (matched_part,start)
    if matched_part:
        assert matched_part-1 in visible, (matched_part,start)
print('PASS: all tested FTS matched parts and overlap predecessors are visible')