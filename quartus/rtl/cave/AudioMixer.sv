module AudioMixer(
  input         clock,
  input  [13:0] io_in_4,
  input  [13:0] io_in_3,
  input  [15:0] io_in_2,
  input  [15:0] io_in_1,
  input  [15:0] io_in_0,
  output [15:0] io_out
);

  reg  [21:0] io_out_REG;
  wire [18:0] _sum_T_3 = 19'({{3{io_in_1[15]}}, io_in_1} * 19'h3);
  wire [21:0] _sum_T_7 = 22'({{6{io_in_3[13]}}, io_in_3, 2'h0} * 22'h1A);
  wire [22:0] _sum_T_10 =
    23'({{3{io_in_0[15]}}, io_in_0, 4'h0} + {{4{_sum_T_3[18]}}, _sum_T_3});
  wire [23:0] _sum_T_11 =
    24'({_sum_T_10[22], _sum_T_10} + {{4{io_in_2[15]}}, io_in_2, 4'h0});
  wire [24:0] _sum_T_12 = 25'({_sum_T_11[23], _sum_T_11} + {{3{_sum_T_7[21]}}, _sum_T_7});
  wire [25:0] sum = 26'({_sum_T_12[24], _sum_T_12} + {{6{io_in_4[13]}}, io_in_4, 6'h0});
  wire [21:0] _io_out_T_1 = $signed(sum[25:4]) < -22'sh8000 ? 22'h3F8000 : sum[25:4];
  always @(posedge clock)
    io_out_REG <= $signed(_io_out_T_1) < 22'sh7FFF ? _io_out_T_1 : 22'h7FFF;
  assign io_out = io_out_REG[15:0];
endmodule

