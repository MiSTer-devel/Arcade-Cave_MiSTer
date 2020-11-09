#!/bin/bash

cd quartus
quartus_sh --flow compile cave && quartus_pgm -m jtag -c 1 -o "p;output_files/cave.sof@2"
