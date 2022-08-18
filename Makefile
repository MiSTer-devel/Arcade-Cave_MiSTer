.PHONY: idea build program copy test clean

idea:
	./mill mill.scalalib.GenIdea/idea

build:
	./mill cave.run
	cd quartus; quartus_sh --flow compile cave

program:
	cd quartus; quartus_pgm -m jtag -c DE-SoC -o "p;output_files/cave.sof@2"

copy:
	scp quartus/output_files/cave.rbf  root@mister-1:/media/fat/_Arcade/cores

test:
	./mill _.test

clean:
	rm -rf out quartus/core/Cave.* quartus/db quartus/incremental_db quartus/output_files test_run_dir
