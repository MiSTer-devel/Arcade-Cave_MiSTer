module Cache(
  input         clock,
  input         reset,
  input         io_enable,
  input         io_in_rd,
  input         io_in_wr,
  input  [6:0]  io_in_addr,
  input  [15:0] io_in_din,
  output [15:0] io_in_dout,
  output        io_in_wait_n,
  output        io_in_valid,
  output        io_out_rd,
  output        io_out_wr,
  output [24:0] io_out_addr,
  output [15:0] io_out_din,
  input  [15:0] io_out_dout,
  input         io_out_wait_n,
  input         io_out_valid
);

  wire [15:0] nextCacheEntry_line_words_3;
  wire [15:0] nextCacheEntry_line_words_2;
  wire [15:0] nextCacheEntry_line_words_1;
  wire [15:0] nextCacheEntry_line_words_0;
  wire [3:0]  nextCacheEntry_tag;
  wire        nextCacheEntry_dirty;
  wire        nextCacheEntry_valid;
  wire        _cacheEntryMemB_ext_R0_addr;
  wire        _cacheEntryMemB_ext_R0_en = 1'h1;
  wire        _cacheEntryMemB_ext_W0_en;
  wire [69:0] _cacheEntryMemB_ext_R0_data;
  wire        _cacheEntryMemA_ext_R0_addr;
  wire        _cacheEntryMemA_ext_R0_en = 1'h1;
  wire        _cacheEntryMemA_ext_W0_en;
  wire [69:0] _cacheEntryMemA_ext_R0_data;
  reg  [3:0]  stateReg;
  reg  [1:0]  offsetReg;
  reg         requestReg_rd;
  reg         requestReg_wr;
  reg  [3:0]  requestReg_addr_tag;
  reg         requestReg_addr_index;
  reg  [1:0]  requestReg_addr_offset;
  reg  [15:0] requestReg_din;
  reg  [15:0] doutReg;
  reg         validReg;
  reg  [1:0]  lruReg;
  reg         wayReg;
  wire [69:0] _GEN =
    {nextCacheEntry_valid,
     nextCacheEntry_dirty,
     nextCacheEntry_tag,
     nextCacheEntry_line_words_3,
     nextCacheEntry_line_words_2,
     nextCacheEntry_line_words_1,
     nextCacheEntry_line_words_0};
  wire        _io_debug_check_T = stateReg == 4'h2;
  reg         cacheEntryReg_valid;
  reg         cacheEntryReg_dirty;
  reg  [3:0]  cacheEntryReg_tag;
  reg  [15:0] cacheEntryReg_line_words_0;
  reg  [15:0] cacheEntryReg_line_words_1;
  reg  [15:0] cacheEntryReg_line_words_2;
  reg  [15:0] cacheEntryReg_line_words_3;
  wire        _io_debug_write_T = stateReg == 4'h8;
  assign nextCacheEntry_valid = _io_debug_write_T & cacheEntryReg_valid;
  assign nextCacheEntry_dirty = _io_debug_write_T & cacheEntryReg_dirty;
  assign nextCacheEntry_tag = _io_debug_write_T ? cacheEntryReg_tag : 4'h0;
  assign nextCacheEntry_line_words_0 =
    _io_debug_write_T ? cacheEntryReg_line_words_0 : 16'h0;
  assign nextCacheEntry_line_words_1 =
    _io_debug_write_T ? cacheEntryReg_line_words_1 : 16'h0;
  assign nextCacheEntry_line_words_2 =
    _io_debug_write_T ? cacheEntryReg_line_words_2 : 16'h0;
  assign nextCacheEntry_line_words_3 =
    _io_debug_write_T ? cacheEntryReg_line_words_3 : 16'h0;
  wire        _GEN_0 = stateReg == 4'h0;
  wire        _io_debug_evict_T = stateReg == 4'h5;
  wire        _io_debug_evictWait_T = stateReg == 4'h6;
  reg         initCounter;
  reg  [1:0]  burstCounter;
  wire        _io_debug_idle_T = stateReg == 4'h1;
  wire        start = io_enable & (io_in_rd | io_in_wr) & _io_debug_idle_T;
  wire        hitA =
    _cacheEntryMemA_ext_R0_data[69]
    & _cacheEntryMemA_ext_R0_data[67:64] == requestReg_addr_tag;
  wire        hit =
    hitA | _cacheEntryMemB_ext_R0_data[69]
    & _cacheEntryMemB_ext_R0_data[67:64] == requestReg_addr_tag;
  wire        io_out_rd_0 = stateReg == 4'h3;
  wire [3:0]  _outAddr_addr_T_1_tag =
    io_out_rd_0 ? requestReg_addr_tag : cacheEntryReg_tag;
  wire [1:0]  _outAddr_addr_T_1_offset = io_out_rd_0 ? requestReg_addr_offset : 2'h0;
  wire [1:0]  _nextWay_T = lruReg >> io_in_addr[3];
  wire        _nextWay_T_2 = start ? _nextWay_T[0] : wayReg;
  wire [1:0]  _n_T = 2'(requestReg_addr_offset + burstCounter);
  wire        _GEN_1 = _n_T == 2'h0;
  wire        _GEN_2 = _n_T == 2'h1;
  wire        _GEN_3 = _n_T == 2'h2;
  reg  [15:0] casez_tmp;
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
  reg  [15:0] casez_tmp_0;
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
  reg  [15:0] casez_tmp_1;
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
  wire        _GEN_4 = _GEN_0 | _io_debug_idle_T;
  wire        _GEN_5 = _GEN_4 | ~(_io_debug_check_T & hit);
  wire        nextWay = _GEN_5 ? _nextWay_T_2 : ~hitA;
  reg  [15:0] casez_tmp_2;
  always @(*) begin
    casez (burstCounter)
      2'b00:
        casez_tmp_2 = cacheEntryReg_line_words_0;
      2'b01:
        casez_tmp_2 = cacheEntryReg_line_words_1;
      2'b10:
        casez_tmp_2 = cacheEntryReg_line_words_2;
      default:
        casez_tmp_2 = cacheEntryReg_line_words_3;
    endcase
  end // always @(*)
  wire [1:0]  _GEN_6 = {1'h0, requestReg_addr_index};
  wire [1:0]  _lruReg_T = 2'h1 << _GEN_6;
  wire [1:0]  _lruReg_T_7 = 2'h1 << _GEN_6;
  wire [1:0]  _lruReg_T_14 = 2'h1 << _GEN_6;
  wire [15:0] _cacheEntryReg_words_T_2 = {requestReg_din[7:0], requestReg_din[15:8]};
  wire        _cacheEntryReg_T_valid =
    nextWay ? _cacheEntryMemB_ext_R0_data[69] : _cacheEntryMemA_ext_R0_data[69];
  wire        _GEN_7 = _io_debug_check_T ? _cacheEntryReg_T_valid : cacheEntryReg_valid;
  wire        _io_debug_fillWait_T = stateReg == 4'h4;
  wire        dirty =
    ~wayReg & _cacheEntryMemA_ext_R0_data[68]
    & _cacheEntryMemA_ext_R0_data[67:64] != requestReg_addr_tag | wayReg
    & _cacheEntryMemB_ext_R0_data[68]
    & _cacheEntryMemB_ext_R0_data[67:64] != requestReg_addr_tag;
  wire        _GEN_8 = _io_debug_fillWait_T & io_out_valid;
  wire        _io_debug_merge_T = stateReg == 4'h7;
  wire        _GEN_9 = _io_debug_check_T & hit & requestReg_rd;
  wire        burstCounterEnable =
    _io_debug_fillWait_T & io_out_valid | (_io_debug_evict_T | _io_debug_evictWait_T)
    & io_out_wait_n;
  wire        burstCounterWrap = burstCounterEnable & (&burstCounter);
  wire        _cacheEntryReg_T_dirty =
    nextWay ? _cacheEntryMemB_ext_R0_data[68] : _cacheEntryMemA_ext_R0_data[68];
  wire        _GEN_10 =
    _GEN_8 | ~_io_debug_check_T ? cacheEntryReg_dirty : _cacheEntryReg_T_dirty;
  always @(posedge clock) begin
    if (reset) begin
      stateReg <= 4'h0;
      initCounter <= 1'h0;
      burstCounter <= 2'h0;
    end
    else begin
      if (_GEN_0) begin
        if (_GEN_0 & initCounter)
          stateReg <= 4'h1;
        else if (_io_debug_merge_T)
          stateReg <= 4'h8;
        initCounter <= 1'(initCounter - 1'h1);
      end
      else if (_io_debug_idle_T) begin
        if (start)
          stateReg <= 4'h2;
        else if (_io_debug_merge_T)
          stateReg <= 4'h8;
      end
      else if (_io_debug_check_T) begin
        if (hit)
          stateReg <= requestReg_rd ? 4'h1 : 4'h7;
        else if (dirty)
          stateReg <= 4'h5;
        else if (hit) begin
          if (_io_debug_merge_T)
            stateReg <= 4'h8;
        end
        else
          stateReg <= 4'h3;
      end
      else if (io_out_rd_0) begin
        if (io_out_wait_n)
          stateReg <= 4'h4;
        else if (_io_debug_merge_T)
          stateReg <= 4'h8;
      end
      else if (_io_debug_fillWait_T) begin
        if (burstCounterWrap)
          stateReg <= requestReg_wr ? 4'h7 : 4'h8;
        else if (_io_debug_merge_T)
          stateReg <= 4'h8;
      end
      else if (_io_debug_evict_T) begin
        if (io_out_wait_n)
          stateReg <= 4'h6;
        else if (_io_debug_merge_T)
          stateReg <= 4'h8;
      end
      else if (_io_debug_evictWait_T) begin
        if (burstCounterWrap)
          stateReg <= 4'h3;
        else if (_io_debug_merge_T)
          stateReg <= 4'h8;
      end
      else if (_io_debug_merge_T)
        stateReg <= 4'h8;
      else if (_io_debug_write_T)
        stateReg <= 4'h1;
      if (burstCounterEnable)
        burstCounter <= 2'(burstCounter + 2'h1);
    end
    if (start) begin
      offsetReg <= io_in_addr[2:1];
      requestReg_rd <= io_in_rd;
      requestReg_wr <= io_in_wr;
      requestReg_addr_tag <= {1'h0, io_in_addr[6:4]};
      requestReg_addr_index <= io_in_addr[3];
      requestReg_addr_offset <= io_in_addr[2:1];
      requestReg_din <= io_in_din;
    end
    if (_GEN_4 | ~_GEN_9) begin
      if (_GEN_8)
        doutReg <= {casez_tmp[7:0], casez_tmp[15:8]};
    end
    else
      doutReg <=
        hitA
          ? {casez_tmp_0[7:0], casez_tmp_0[15:8]}
          : {casez_tmp_1[7:0], casez_tmp_1[15:8]};
    validReg <= ~_GEN_4 & _GEN_9 | _GEN_8 & requestReg_rd & burstCounter == 2'h0;
    if (_GEN_4 | ~_io_debug_check_T) begin
    end
    else if (hit)
      lruReg <= hitA ? lruReg | _lruReg_T : ~(~lruReg | _lruReg_T);
    else if (dirty)
      lruReg <= wayReg ? ~(~lruReg | _lruReg_T_7) : lruReg | _lruReg_T_7;
    else if (~hit)
      lruReg <= wayReg ? ~(~lruReg | _lruReg_T_14) : lruReg | _lruReg_T_14;
    if (_GEN_5) begin
      if (start)
        wayReg <= _nextWay_T[0];
    end
    else
      wayReg <= ~hitA;
    if (_io_debug_merge_T) begin
      if (offsetReg == 2'h0)
        cacheEntryReg_line_words_0 <= _cacheEntryReg_words_T_2;
      if (offsetReg == 2'h1)
        cacheEntryReg_line_words_1 <= _cacheEntryReg_words_T_2;
      if (offsetReg == 2'h2)
        cacheEntryReg_line_words_2 <= _cacheEntryReg_words_T_2;
      if (&offsetReg)
        cacheEntryReg_line_words_3 <= _cacheEntryReg_words_T_2;
    end
    else begin
      cacheEntryReg_valid <= _GEN_8 | _GEN_7;
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
          nextWay
            ? _cacheEntryMemB_ext_R0_data[67:64]
            : _cacheEntryMemA_ext_R0_data[67:64];
        cacheEntryReg_line_words_0 <=
          nextWay ? _cacheEntryMemB_ext_R0_data[15:0] : _cacheEntryMemA_ext_R0_data[15:0];
        cacheEntryReg_line_words_1 <=
          nextWay
            ? _cacheEntryMemB_ext_R0_data[31:16]
            : _cacheEntryMemA_ext_R0_data[31:16];
        cacheEntryReg_line_words_2 <=
          nextWay
            ? _cacheEntryMemB_ext_R0_data[47:32]
            : _cacheEntryMemA_ext_R0_data[47:32];
        cacheEntryReg_line_words_3 <=
          nextWay
            ? _cacheEntryMemB_ext_R0_data[63:48]
            : _cacheEntryMemA_ext_R0_data[63:48];
      end
    end
    cacheEntryReg_dirty <= _io_debug_merge_T | _GEN_10;
  end // always @(posedge)
  assign _cacheEntryMemA_ext_R0_addr = io_in_addr[3];
  assign _cacheEntryMemA_ext_W0_en = _GEN_0 | _io_debug_write_T & ~wayReg;
  cacheEntryMem_2x70 cacheEntryMemA_ext (
    .R0_addr (_cacheEntryMemA_ext_R0_addr),
    .R0_en   (_cacheEntryMemA_ext_R0_en),
    .R0_clk  (clock),
    .R0_data (_cacheEntryMemA_ext_R0_data),
    .W0_addr (requestReg_addr_index),
    .W0_en   (_cacheEntryMemA_ext_W0_en),
    .W0_clk  (clock),
    .W0_data (_GEN)
  );
  assign _cacheEntryMemB_ext_R0_addr = io_in_addr[3];
  assign _cacheEntryMemB_ext_W0_en = _GEN_0 | _io_debug_write_T & wayReg;
  cacheEntryMem_2x70 cacheEntryMemB_ext (
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
  assign io_out_rd = io_out_rd_0;
  assign io_out_wr = _io_debug_evict_T | _io_debug_evictWait_T;
  assign io_out_addr =
    {17'h0, _outAddr_addr_T_1_tag, requestReg_addr_index, _outAddr_addr_T_1_offset, 1'h0};
  assign io_out_din = casez_tmp_2;
endmodule

