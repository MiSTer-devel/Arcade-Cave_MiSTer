.PHONY: build program test clean

build:
	sbt compile run
	cd quartus; quartus_sh --flow compile cave

program:
	cd quartus; quartus_pgm -m jtag -c 1 -o "p;output_files/cave.sof@2"

test:
	sbt test

clean:
	rm -rf project/target quartus/rtl/ChiselTop.v quartus/rtl/Main.* quartus/db quartus/incremental_db quartus/output_files target test_run_dir
