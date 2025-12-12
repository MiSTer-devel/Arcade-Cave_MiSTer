module ReadDataFreezer_1(
  input         clock,
  input         reset,
  input         io_targetClock,
  input         io_in_rd,
  input  [24:0] io_in_addr,
  output [7:0]  io_in_dout,
  output        io_in_wait_n,
  output        io_in_valid,
  output        io_out_rd,
  output [24:0] io_out_addr,
  input  [7:0]  io_out_dout,
  input         io_out_wait_n,
  input         io_out_valid
);

  reg        clear_s;
  reg        clear_REG;
  wire       clear = clear_s ^ clear_REG;
  reg        wait_n_enableReg;
  reg        valid_enableReg;
  wire       valid = io_out_valid | valid_enableReg & ~clear;
  reg  [7:0] data_dataReg;
  reg        data_enableReg;
  reg        pendingRead;
  reg        clearRead_REG;
  wire       clearRead = clear & clearRead_REG;
  always @(posedge io_targetClock) begin
    if (reset)
      clear_s <= 1'h0;
    else
      clear_s <= ~clear_s;
  end // always @(posedge)
  always @(posedge clock) begin
    clear_REG <= clear_s;
    if (io_out_valid)
      data_dataReg <= io_out_dout;
    clearRead_REG <= valid;
    if (reset) begin
      wait_n_enableReg <= 1'h0;
      valid_enableReg <= 1'h0;
      data_enableReg <= 1'h0;
      pendingRead <= 1'h0;
    end
    else begin
      wait_n_enableReg <= io_out_wait_n | ~clear & wait_n_enableReg;
      valid_enableReg <= io_out_valid | ~clear & valid_enableReg;
      data_enableReg <= ~clear & (io_out_valid | data_enableReg);
      pendingRead <= io_in_rd & io_out_wait_n | ~clearRead & pendingRead;
    end
  end // always @(posedge)
  assign io_in_dout = data_enableReg & ~clear ? data_dataReg : io_out_dout;
  assign io_in_wait_n = io_out_wait_n | wait_n_enableReg & ~clear;
  assign io_in_valid = valid;
  assign io_out_rd = io_in_rd & (~pendingRead | clearRead);
  assign io_out_addr = io_in_addr;
endmodule

