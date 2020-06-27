.PHONY: test run clean

rtl/Cave.v:
	@sbt compile

test:
	@sbt test

run: rtl/Cave.v
	@sbt run

clean:
	rm -rf project/target rtl/Cave.* target test_run_dir
