.PHONY: idea build program test clean

idea:
	./mill mill.scalalib.GenIdea/idea

build:
	./mill cave.run
	cd quartus; quartus_sh --flow compile cave

program:
	cd quartus; quartus_pgm -m jtag -c DE-SoC -o "p;output_files/cave.sof@2"

test:
	./mill cave.test

clean:
	rm -rf out quartus/rtl/ChiselTop.v quartus/rtl/Main.* quartus/db quartus/incremental_db quartus/output_files test_run_dir
