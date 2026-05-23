#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")/.."

quartus_sh --flow compile Arcade-Cave
quartus_pgm -m jtag -c 1 -o "p;output_files/Arcade-Cave.sof@2"
