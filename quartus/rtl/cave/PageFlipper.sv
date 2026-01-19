module PageFlipper(
  input         clock,
  input         reset,
  input         io_swapWrite,
  output [31:0] io_addrRead,
  output [31:0] io_addrWrite
);

  reg [1:0] rdIndexReg;
  reg [1:0] wrIndexReg;
  always @(posedge clock) begin
    if (reset) begin
      rdIndexReg <= 2'h0;
      wrIndexReg <= 2'h1;
    end
    else if (io_swapWrite) begin
      rdIndexReg <= {1'h0, wrIndexReg[0]};
      wrIndexReg <= {1'h0, ~(wrIndexReg[0])};
    end
  end // always @(posedge)
  assign io_addrRead = {11'h121, rdIndexReg, 19'h0};
  assign io_addrWrite = {11'h121, wrIndexReg, 19'h0};
endmodule

