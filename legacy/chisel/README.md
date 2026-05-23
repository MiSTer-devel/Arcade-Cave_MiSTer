# Legacy Chisel Reference

This directory contains the original Chisel source and Mill build that once
generated the Cave SystemVerilog.

The normal MiSTer/Quartus build does not use this directory. Keep it as a
readable reference for the hand-maintained HDL under `../../rtl/cave/`.

Useful commands from the repository root:

```sh
make generate-rtl
make test
```

Both commands require a working Scala/JDK environment. The generated HDL target
is `../generated/cave/`, intentionally separate from active `rtl/` sources.
