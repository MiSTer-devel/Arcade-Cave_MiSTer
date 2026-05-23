module CaveClockEnable #(
  parameter [16:0] STEP = 17'h2000
) (
  input  clock,
  output reg enable
);
  reg [15:0] accumulator;
  wire [16:0] next_accumulator = {1'b0, accumulator} + STEP;

  always @(posedge clock) begin
    accumulator <= next_accumulator[15:0];
    enable <= next_accumulator[16];
  end
endmodule
