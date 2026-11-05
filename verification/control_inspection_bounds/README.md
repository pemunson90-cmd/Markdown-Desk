# Physical control inspection limits

Run `bash verification/control_inspection_bounds/run.sh` with JDK17 on PATH.
The runner compiles the production control parser and executes physical fixtures
under a 32MiB Java heap. It covers exact/one-under enrollment, enrollment stage,
shared parent/directory bytes and slots, unknown/staged records, 256/257 entries,
16GiB sparse regular records in both locations, invalid arguments and nonregular
entries. Limit failures must throw IOException rather than return a partial
Inspection. Generic inspection remains usable with its existing blocked states.

These are physical input cap tests, not a whole-heap bound, workspace precharge,
portable authority proof, or the original 512KiB SAME-parent funding requirement.
Native filenames remain subject to the existing filesystem component constraints;
unknown names do not escape Entry as arbitrary user strings. Negative regular
sizes are rejected in production but cannot be produced by the native fixture.
