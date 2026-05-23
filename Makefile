.PHONY: idea generate-rtl build program copy test clean

CORE := Arcade-Cave
RBF := output_files/$(CORE).rbf
SOF := output_files/$(CORE).sof
CHISEL_DIR := legacy/chisel

idea:
	cd $(CHISEL_DIR) && ./bin/mill mill.scalalib.GenIdea/idea

generate-rtl:
	cd $(CHISEL_DIR) && ./bin/mill cave.run

build:
	quartus_sh --flow compile $(CORE)

program:
	quartus_pgm -m jtag -c DE-SoC -o "p;$(SOF)@2"

copy:
	scp $(RBF) root@mister-1:/media/fat/_Arcade/cores

test:
	cd $(CHISEL_DIR) && ./bin/mill _.test

clean:
	rm -rf db greybox_tmp incremental_db output_files test_run_dir legacy/chisel/out legacy/generated build_id.v c5_pin_model_dump.txt jtag.cdf *.qws *.qdf
