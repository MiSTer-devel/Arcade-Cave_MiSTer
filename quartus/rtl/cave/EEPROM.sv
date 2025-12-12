module EEPROM(
  input         clock,
  input         reset,
  output        io_mem_rd,
  output        io_mem_wr,
  output [6:0]  io_mem_addr,
  output [15:0] io_mem_din,
  input  [15:0] io_mem_dout,
  input         io_mem_wait_n,
  input         io_mem_valid,
  input         io_serial_cs,
  input         io_serial_sck,
  input         io_serial_sdi,
  output        io_serial_sdo
);

  reg  [2:0]  stateReg;
  reg  [16:0] counterReg;
  reg  [5:0]  addrReg;
  reg  [15:0] dataReg;
  reg  [1:0]  opcodeReg;
  reg         serialReg;
  reg         writeAllReg;
  reg         writeEnableReg;
  reg         sckRising_REG;
  wire        sckRising = io_serial_sck & ~sckRising_REG;
  wire        read = opcodeReg == 2'h2;
  wire        write = opcodeReg == 2'h1 & writeEnableReg;
  wire        erase = (&opcodeReg) & writeEnableReg;
  wire        _disableWrite_T = opcodeReg == 2'h0;
  wire        writeAll = _disableWrite_T & addrReg[4:3] == 2'h1 & writeEnableReg;
  wire        eraseAll = _disableWrite_T & addrReg[4:3] == 2'h2 & writeEnableReg;
  wire        _GEN = sckRising & counterReg[0];
  wire        _GEN_0 = writeAll | eraseAll;
  wire        _GEN_1 = read | write | erase;
  reg  [5:0]  casez_tmp;
  wire [5:0]  _GEN_2 =
    ~(counterReg[0]) | _GEN_1 | ~_GEN_0 ? {addrReg[4:0], io_serial_sdi} : 6'h0;
  wire [5:0]  _GEN_3 =
    stateReg == 3'h3 | stateReg == 3'h4 | ~(stateReg == 3'h5 & io_mem_wait_n)
      ? addrReg
      : 6'(addrReg + 6'h1);
  always @(*) begin
    casez (stateReg)
      3'b000:
        casez_tmp = 6'h0;
      3'b001:
        casez_tmp = addrReg;
      3'b010:
        casez_tmp = sckRising ? _GEN_2 : addrReg;
      3'b011:
        casez_tmp = addrReg;
      3'b100:
        casez_tmp = addrReg;
      3'b101:
        casez_tmp = _GEN_3;
      3'b110:
        casez_tmp = _GEN_3;
      default:
        casez_tmp = _GEN_3;
    endcase
  end // always @(*)
  wire        _GEN_4 = (&stateReg) & sckRising;
  reg  [15:0] casez_tmp_0;
  wire [15:0] _GEN_5 = io_mem_valid ? io_mem_dout : dataReg;
  always @(*) begin
    casez (stateReg)
      3'b000:
        casez_tmp_0 = 16'hFFFF;
      3'b001:
        casez_tmp_0 = dataReg;
      3'b010:
        casez_tmp_0 = dataReg;
      3'b011:
        casez_tmp_0 = _GEN_5;
      3'b100:
        casez_tmp_0 = _GEN_5;
      3'b101:
        casez_tmp_0 = dataReg;
      3'b110:
        casez_tmp_0 = sckRising ? {dataReg[14:0], io_serial_sdi} : dataReg;
      default:
        casez_tmp_0 = _GEN_4 ? {dataReg[14:0], 1'h0} : dataReg;
    endcase
  end // always @(*)
  reg         casez_tmp_1;
  wire        _GEN_6 = _GEN_4 ? dataReg[15] : serialReg;
  always @(*) begin
    casez (stateReg)
      3'b000:
        casez_tmp_1 = _GEN_6;
      3'b001:
        casez_tmp_1 = serialReg;
      3'b010:
        casez_tmp_1 = ~(sckRising & counterReg[0] & read) & serialReg;
      3'b011:
        casez_tmp_1 = serialReg;
      3'b100:
        casez_tmp_1 = serialReg;
      3'b101:
        casez_tmp_1 = serialReg;
      3'b110:
        casez_tmp_1 = ~sckRising & serialReg;
      default:
        casez_tmp_1 = _GEN_6;
    endcase
  end // always @(*)
  reg  [2:0]  casez_tmp_2;
  wire [2:0]  _GEN_7 = eraseAll ? 3'h5 : 3'h0;
  wire [2:0]  _GEN_8 = writeAll ? 3'h6 : _GEN_7;
  wire [2:0]  _GEN_9 = erase ? 3'h5 : _GEN_8;
  wire [2:0]  _GEN_10 = write ? 3'h6 : _GEN_9;
  wire [2:0]  _GEN_11 = read ? 3'h3 : _GEN_10;
  wire [2:0]  _GEN_12 = io_mem_wait_n ? 3'h4 : stateReg;
  always @(*) begin
    casez (stateReg)
      3'b000:
        casez_tmp_2 = io_serial_cs ? 3'h1 : stateReg;
      3'b001:
        casez_tmp_2 = sckRising & io_serial_sdi ? 3'h2 : stateReg;
      3'b010:
        casez_tmp_2 = _GEN ? _GEN_11 : stateReg;
      3'b011:
        casez_tmp_2 = io_mem_valid ? 3'h7 : _GEN_12;
      3'b100:
        casez_tmp_2 = io_mem_valid ? 3'h7 : stateReg;
      3'b101:
        casez_tmp_2 = io_mem_wait_n & (~writeAllReg | (&addrReg)) ? 3'h0 : stateReg;
      3'b110:
        casez_tmp_2 = _GEN ? 3'h5 : stateReg;
      default:
        casez_tmp_2 = (&stateReg) & _GEN ? 3'h0 : stateReg;
    endcase
  end // always @(*)
  wire [16:0] _GEN_13 = {1'h0, counterReg[16:1]};
  wire        _io_debug_command_T = stateReg == 3'h2;
  wire        _io_debug_idle_T = stateReg == 3'h0;
  wire        _GEN_14 = stateReg == 3'h1;
  wire        _GEN_15 = _io_debug_idle_T | _GEN_14;
  wire        _GEN_16 = _io_debug_command_T & _GEN;
  wire        _GEN_17 =
    (_io_debug_command_T | stateReg == 3'h6 | (&stateReg)) & sckRising;
  always @(posedge clock) begin
    if (reset) begin
      stateReg <= 3'h0;
      counterReg <= 17'h0;
      serialReg <= 1'h1;
      writeAllReg <= 1'h0;
      writeEnableReg <= 1'h0;
    end
    else begin
      if (io_serial_cs)
        stateReg <= casez_tmp_2;
      else
        stateReg <= 3'h0;
      if (_io_debug_idle_T)
        counterReg <= 17'h80;
      else if (_GEN_14 | ~_GEN_16) begin
        if (_GEN_17)
          counterReg <= _GEN_13;
      end
      else if (read)
        counterReg <= 17'h10000;
      else if (write | ~(erase | ~writeAll))
        counterReg <= 17'h8000;
      else if (_GEN_17)
        counterReg <= _GEN_13;
      serialReg <= _io_debug_idle_T | casez_tmp_1;
      writeAllReg <=
        ~_io_debug_idle_T & (~_GEN_14 & _GEN_16 & ~_GEN_1 & _GEN_0 | writeAllReg);
      if (_GEN_15 | ~_GEN_16 | read | write | erase | _GEN_0) begin
      end
      else
        writeEnableReg <=
          _disableWrite_T & (&(addrReg[4:3])) | ~(_disableWrite_T & addrReg[4:3] == 2'h0)
          & writeEnableReg;
    end
    addrReg <= casez_tmp;
    dataReg <= casez_tmp_0;
    if (_GEN_15 | ~(_io_debug_command_T & sckRising)) begin
    end
    else
      opcodeReg <= addrReg[5:4];
    sckRising_REG <= io_serial_sck;
  end // always @(posedge)
  assign io_mem_rd = stateReg == 3'h3;
  assign io_mem_wr = stateReg == 3'h5;
  assign io_mem_addr = {addrReg, 1'h0};
  assign io_mem_din = dataReg;
  assign io_serial_sdo = serialReg;
endmodule

