module BurstBuffer(
  input         clock,
  input         reset,
  input         io_in_wr,
  input  [26:0] io_in_addr,
  input  [15:0] io_in_din,
  output        io_out_wr,
  output [31:0] io_out_addr,
  output [63:0] io_out_din,
  input         io_out_burstDone
);

  reg         writePendingReg;
  reg  [63:0] lineReg_words_0;
  reg  [26:0] addrReg;
  reg         busyReg;
  reg  [1:0]  wordCounter;
  wire [15:0] words_3 = (&wordCounter) ? io_in_din : lineReg_words_0[63:48];
  wire [15:0] words_2 = wordCounter == 2'h2 ? io_in_din : lineReg_words_0[47:32];
  wire [15:0] words_1 = wordCounter == 2'h1 ? io_in_din : lineReg_words_0[31:16];
  wire [15:0] words_0 = wordCounter == 2'h0 ? io_in_din : lineReg_words_0[15:0];
  wire        latchData = io_in_wr & ~writePendingReg;
  always @(posedge clock) begin
    if (reset) begin
      writePendingReg <= 1'h0;
      busyReg <= 1'h0;
      wordCounter <= 2'h0;
    end
    else begin
      writePendingReg <=
        ~io_out_burstDone & (latchData & (&wordCounter) | writePendingReg);
      busyReg <= ~io_out_burstDone & (io_in_wr | busyReg);
      if (latchData)
        wordCounter <= 2'(wordCounter + 2'h1);
    end
    if (latchData)
      lineReg_words_0 <= {words_3, words_2, words_1, words_0};
    if (io_in_wr & ~busyReg)
      addrReg <= io_in_addr;
  end // always @(posedge)
  assign io_out_wr = writePendingReg;
  assign io_out_addr = {5'h0, addrReg[26:3], 3'h0};
  assign io_out_din = lineReg_words_0;
endmodule

