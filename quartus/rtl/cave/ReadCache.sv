module ReadCache(
  input         clock,
  input         reset,
  input         io_enable,
  input         io_in_rd,
  input  [19:0] io_in_addr,
  output [15:0] io_in_dout,
  output        io_in_wait_n,
  output        io_in_valid,
  output        io_out_rd,
  output [24:0] io_out_addr,
  input  [15:0] io_out_dout,
  input         io_out_wait_n,
  input         io_out_valid
);

  wire [15:0]  nextCacheEntry_line_words_3;
  wire [15:0]  nextCacheEntry_line_words_2;
  wire [15:0]  nextCacheEntry_line_words_1;
  wire [15:0]  nextCacheEntry_line_words_0;
  wire [10:0]  nextCacheEntry_tag;
  wire         nextCacheEntry_dirty;
  wire         nextCacheEntry_valid;
  wire [6:0]   _cacheEntryMemB_ext_R0_addr;
  wire         _cacheEntryMemB_ext_R0_en = 1'h1;
  wire         _cacheEntryMemB_ext_W0_en;
  wire [76:0]  _cacheEntryMemB_ext_R0_data;
  wire [6:0]   _cacheEntryMemA_ext_R0_addr;
  wire         _cacheEntryMemA_ext_R0_en = 1'h1;
  wire         _cacheEntryMemA_ext_W0_en;
  wire [76:0]  _cacheEntryMemA_ext_R0_data;
  reg  [2:0]   stateReg;
  reg  [1:0]   offsetReg;
  reg          requestReg_rd;
  reg  [10:0]  requestReg_addr_tag;
  reg  [6:0]   requestReg_addr_index;
  reg  [1:0]   requestReg_addr_offset;
  reg  [15:0]  doutReg;
  reg          validReg;
  reg  [127:0] lruReg;
  reg          wayReg;
  wire [76:0]  _GEN =
    {nextCacheEntry_valid,
     nextCacheEntry_dirty,
     nextCacheEntry_tag,
     nextCacheEntry_line_words_3,
     nextCacheEntry_line_words_2,
     nextCacheEntry_line_words_1,
     nextCacheEntry_line_words_0};
  wire         _io_debug_check_T = stateReg == 3'h2;
  reg          cacheEntryReg_valid;
  reg          cacheEntryReg_dirty;
  reg  [10:0]  cacheEntryReg_tag;
  reg  [15:0]  cacheEntryReg_line_words_0;
  reg  [15:0]  cacheEntryReg_line_words_1;
  reg  [15:0]  cacheEntryReg_line_words_2;
  reg  [15:0]  cacheEntryReg_line_words_3;
  wire         _io_debug_write_T = stateReg == 3'h5;
  assign nextCacheEntry_valid = _io_debug_write_T & cacheEntryReg_valid;
  assign nextCacheEntry_dirty = _io_debug_write_T & cacheEntryReg_dirty;
  assign nextCacheEntry_tag = _io_debug_write_T ? cacheEntryReg_tag : 11'h0;
  assign nextCacheEntry_line_words_0 =
    _io_debug_write_T ? cacheEntryReg_line_words_0 : 16'h0;
  assign nextCacheEntry_line_words_1 =
    _io_debug_write_T ? cacheEntryReg_line_words_1 : 16'h0;
  assign nextCacheEntry_line_words_2 =
    _io_debug_write_T ? cacheEntryReg_line_words_2 : 16'h0;
  assign nextCacheEntry_line_words_3 =
    _io_debug_write_T ? cacheEntryReg_line_words_3 : 16'h0;
  wire         _GEN_0 = stateReg == 3'h0;
  wire         _io_debug_fillWait_T = stateReg == 3'h4;
  wire         burstCounterEnable = _io_debug_fillWait_T & io_out_valid;
  reg  [6:0]   initCounter;
  reg  [1:0]   burstCounter;
  wire         _io_debug_idle_T = stateReg == 3'h1;
  wire         start = io_enable & io_in_rd & _io_debug_idle_T;
  wire         hitA =
    _cacheEntryMemA_ext_R0_data[76]
    & _cacheEntryMemA_ext_R0_data[74:64] == requestReg_addr_tag;
  wire         hit =
    hitA | _cacheEntryMemB_ext_R0_data[76]
    & _cacheEntryMemB_ext_R0_data[74:64] == requestReg_addr_tag;
  wire [127:0] _nextWay_T = lruReg >> io_in_addr[9:3];
  wire         _nextWay_T_2 = start ? _nextWay_T[0] : wayReg;
  wire [1:0]   _n_T = 2'(requestReg_addr_offset + burstCounter);
  wire         _GEN_1 = _n_T == 2'h0;
  wire         _GEN_2 = _n_T == 2'h1;
  wire         _GEN_3 = _n_T == 2'h2;
  reg  [15:0]  casez_tmp;
  always @(*) begin
    casez (offsetReg)
      2'b00:
        casez_tmp = _GEN_1 ? io_out_dout : cacheEntryReg_line_words_0;
      2'b01:
        casez_tmp = _GEN_2 ? io_out_dout : cacheEntryReg_line_words_1;
      2'b10:
        casez_tmp = _GEN_3 ? io_out_dout : cacheEntryReg_line_words_2;
      default:
        casez_tmp = (&_n_T) ? io_out_dout : cacheEntryReg_line_words_3;
    endcase
  end // always @(*)
  reg  [15:0]  casez_tmp_0;
  always @(*) begin
    casez (offsetReg)
      2'b00:
        casez_tmp_0 = _cacheEntryMemA_ext_R0_data[15:0];
      2'b01:
        casez_tmp_0 = _cacheEntryMemA_ext_R0_data[31:16];
      2'b10:
        casez_tmp_0 = _cacheEntryMemA_ext_R0_data[47:32];
      default:
        casez_tmp_0 = _cacheEntryMemA_ext_R0_data[63:48];
    endcase
  end // always @(*)
  reg  [15:0]  casez_tmp_1;
  always @(*) begin
    casez (offsetReg)
      2'b00:
        casez_tmp_1 = _cacheEntryMemB_ext_R0_data[15:0];
      2'b01:
        casez_tmp_1 = _cacheEntryMemB_ext_R0_data[31:16];
      2'b10:
        casez_tmp_1 = _cacheEntryMemB_ext_R0_data[47:32];
      default:
        casez_tmp_1 = _cacheEntryMemB_ext_R0_data[63:48];
    endcase
  end // always @(*)
  wire         _GEN_4 = _io_debug_check_T & hit;
  wire         _GEN_5 = _GEN_0 | _io_debug_idle_T;
  wire         _GEN_6 = _GEN_5 | ~_GEN_4;
  wire         nextWay = _GEN_6 ? _nextWay_T_2 : ~hitA;
  reg  [2:0]   casez_tmp_2;
  always @(*) begin
    casez (stateReg)
      3'b000:
        casez_tmp_2 = _GEN_0 & (&initCounter) ? 3'h1 : stateReg;
      3'b001:
        casez_tmp_2 = start ? 3'h2 : stateReg;
      3'b010:
        casez_tmp_2 = {1'h0, ~hit, 1'h1};
      3'b011:
        casez_tmp_2 = io_out_wait_n ? 3'h4 : stateReg;
      3'b100:
        casez_tmp_2 = burstCounterEnable & (&burstCounter) ? 3'h5 : stateReg;
      3'b101:
        casez_tmp_2 = 3'h1;
      3'b110:
        casez_tmp_2 = stateReg;
      default:
        casez_tmp_2 = stateReg;
    endcase
  end // always @(*)
  wire [127:0] _GEN_7 = {121'h0, requestReg_addr_index};
  wire [127:0] _lruReg_T = 128'h1 << _GEN_7;
  wire [127:0] _lruReg_T_7 = 128'h1 << _GEN_7;
  wire [127:0] _lruReg_T_12 = wayReg ? ~(~lruReg | _lruReg_T_7) : lruReg | _lruReg_T_7;
  wire [127:0] _lruReg_T_5 = hitA ? lruReg | _lruReg_T : ~(~lruReg | _lruReg_T);
  wire         _GEN_8 = _io_debug_fillWait_T & io_out_valid;
  wire         _cacheEntryReg_T_valid =
    nextWay ? _cacheEntryMemB_ext_R0_data[76] : _cacheEntryMemA_ext_R0_data[76];
  wire         _GEN_9 = _io_debug_check_T ? _cacheEntryReg_T_valid : cacheEntryReg_valid;
  always @(posedge clock) begin
    if (reset) begin
      stateReg <= 3'h0;
      initCounter <= 7'h0;
      burstCounter <= 2'h0;
    end
    else begin
      stateReg <= casez_tmp_2;
      if (_GEN_0)
        initCounter <= 7'(initCounter + 7'h1);
      if (burstCounterEnable)
        burstCounter <= 2'(burstCounter + 2'h1);
    end
    if (start) begin
      offsetReg <= io_in_addr[2:1];
      requestReg_rd <= io_in_rd;
      requestReg_addr_tag <= {1'h0, io_in_addr[19:10]};
      requestReg_addr_index <= io_in_addr[9:3];
      requestReg_addr_offset <= io_in_addr[2:1];
    end
    if (_GEN_6) begin
      if (_GEN_8)
        doutReg <= {casez_tmp[7:0], casez_tmp[15:8]};
    end
    else
      doutReg <=
        hitA
          ? {casez_tmp_0[7:0], casez_tmp_0[15:8]}
          : {casez_tmp_1[7:0], casez_tmp_1[15:8]};
    validReg <= ~_GEN_5 & _GEN_4 | _GEN_8 & requestReg_rd & burstCounter == 2'h0;
    if (_GEN_5 | ~_io_debug_check_T) begin
    end
    else
      lruReg <= hit ? _lruReg_T_5 : _lruReg_T_12;
    if (_GEN_6) begin
      if (start)
        wayReg <= _nextWay_T[0];
    end
    else
      wayReg <= ~hitA;
    cacheEntryReg_valid <= _GEN_8 | _GEN_9;
    if (_GEN_8 | ~_io_debug_check_T) begin
    end
    else
      cacheEntryReg_dirty <=
        nextWay ? _cacheEntryMemB_ext_R0_data[75] : _cacheEntryMemA_ext_R0_data[75];
    if (_GEN_8) begin
      cacheEntryReg_tag <= requestReg_addr_tag;
      if (_GEN_1)
        cacheEntryReg_line_words_0 <= io_out_dout;
      if (_GEN_2)
        cacheEntryReg_line_words_1 <= io_out_dout;
      if (_GEN_3)
        cacheEntryReg_line_words_2 <= io_out_dout;
      if (&_n_T)
        cacheEntryReg_line_words_3 <= io_out_dout;
    end
    else if (_io_debug_check_T) begin
      cacheEntryReg_tag <=
        nextWay ? _cacheEntryMemB_ext_R0_data[74:64] : _cacheEntryMemA_ext_R0_data[74:64];
      cacheEntryReg_line_words_0 <=
        nextWay ? _cacheEntryMemB_ext_R0_data[15:0] : _cacheEntryMemA_ext_R0_data[15:0];
      cacheEntryReg_line_words_1 <=
        nextWay ? _cacheEntryMemB_ext_R0_data[31:16] : _cacheEntryMemA_ext_R0_data[31:16];
      cacheEntryReg_line_words_2 <=
        nextWay ? _cacheEntryMemB_ext_R0_data[47:32] : _cacheEntryMemA_ext_R0_data[47:32];
      cacheEntryReg_line_words_3 <=
        nextWay ? _cacheEntryMemB_ext_R0_data[63:48] : _cacheEntryMemA_ext_R0_data[63:48];
    end
  end // always @(posedge)
  assign _cacheEntryMemA_ext_R0_addr = io_in_addr[9:3];
  assign _cacheEntryMemA_ext_W0_en = _GEN_0 | _io_debug_write_T & ~wayReg;
  cacheEntryMem_128x77 cacheEntryMemA_ext (
    .R0_addr (_cacheEntryMemA_ext_R0_addr),
    .R0_en   (_cacheEntryMemA_ext_R0_en),
    .R0_clk  (clock),
    .R0_data (_cacheEntryMemA_ext_R0_data),
    .W0_addr (requestReg_addr_index),
    .W0_en   (_cacheEntryMemA_ext_W0_en),
    .W0_clk  (clock),
    .W0_data (_GEN)
  );
  assign _cacheEntryMemB_ext_R0_addr = io_in_addr[9:3];
  assign _cacheEntryMemB_ext_W0_en = _GEN_0 | _io_debug_write_T & wayReg;
  cacheEntryMem_128x77 cacheEntryMemB_ext (
    .R0_addr (_cacheEntryMemB_ext_R0_addr),
    .R0_en   (_cacheEntryMemB_ext_R0_en),
    .R0_clk  (clock),
    .R0_data (_cacheEntryMemB_ext_R0_data),
    .W0_addr (requestReg_addr_index),
    .W0_en   (_cacheEntryMemB_ext_W0_en),
    .W0_clk  (clock),
    .W0_data (_GEN)
  );
  assign io_in_dout = doutReg;
  assign io_in_wait_n = io_enable & _io_debug_idle_T;
  assign io_in_valid = validReg;
  assign io_out_rd = stateReg == 3'h3;
  assign io_out_addr =
    {4'h0, requestReg_addr_tag, requestReg_addr_index, requestReg_addr_offset, 1'h0};
endmodule

