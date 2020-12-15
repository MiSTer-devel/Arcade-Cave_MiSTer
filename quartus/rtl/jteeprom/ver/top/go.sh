#!/bin/bash

iverilog test.v ../../hdl/jt9346.v -o sim && sim -lxt