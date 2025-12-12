module BurstBuffer_1(
  input         clock,
  input         reset,
  input         io_in_wr,
  input  [31:0] io_in_addr,
  input  [63:0] io_in_din,
  output        io_in_wait_n,
  output        io_out_wr,
  output [24:0] io_out_addr,
  output [15:0] io_out_din,
  input         io_out_wait_n,
  input         io_out_burstDone
);

  reg         writePendingReg;
  reg  [15:0] lineReg_words_0;
  reg  [15:0] lineReg_words_1;
  reg  [15:0] lineReg_words_2;
  reg  [15:0] lineReg_words_3;
  reg  [31:0] addrReg;
  reg         busyReg;
  reg  [1:0]  burstCounter;
  reg  [15:0] casez_tmp;
  always @(*) begin
    casez (burstCounter)
      2'b00:
        casez_tmp = lineReg_words_0;
      2'b01:
        casez_tmp = lineReg_words_1;
      2'b10:
        casez_tmp = lineReg_words_2;
      default:
        casez_tmp = lineReg_words_3;
    endcase
  end // always @(*)
  wire        latchData = io_in_wr & ~writePendingReg;
  always @(posedge clock) begin
    if (reset) begin
      writePendingReg <= 1'h0;
      busyReg <= 1'h0;
      burstCounter <= 2'h0;
    end
    else begin
      writePendingReg <= ~io_out_burstDone & (latchData | writePendingReg);
      busyReg <= ~io_out_burstDone & (io_in_wr | busyReg);
      if (writePendingReg & io_out_wait_n)
        burstCounter <= 2'(burstCounter + 2'h1);
    end
    if (latchData) begin
      lineReg_words_0 <= io_in_din[15:0];
      lineReg_words_1 <= io_in_din[31:16];
      lineReg_words_2 <= io_in_din[47:32];
      lineReg_words_3 <= io_in_din[63:48];
    end
    if (io_in_wr & ~busyReg)
      addrReg <= io_in_addr;
  end // always @(posedge)
  assign io_in_wait_n = ~writePendingReg;
  assign io_out_wr = writePendingReg;
  assign io_out_addr = {addrReg[24:1], 1'h0};
  assign io_out_din = casez_tmp;
endmodule

