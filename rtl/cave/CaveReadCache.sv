module CaveReadCache #(
  parameter IN_ADDR_WIDTH = 20,
  parameter IN_DATA_WIDTH = 16,
  parameter OUT_ADDR_WIDTH = 25,
  parameter INDEX_WIDTH = 7,
  parameter TAG_WIDTH = 11
) (
  input                         clock,
  input                         reset,
  input                         io_enable,
  input                         io_in_rd,
  input      [IN_ADDR_WIDTH-1:0] io_in_addr,
  output     [IN_DATA_WIDTH-1:0] io_in_dout,
  output                        io_in_wait_n,
  output                        io_in_valid,
  output                        io_out_rd,
  output     [OUT_ADDR_WIDTH-1:0] io_out_addr,
  input      [15:0]             io_out_dout,
  input                         io_out_wait_n,
  input                         io_out_valid
);
  localparam [2:0] STATE_INIT = 3'd0;
  localparam [2:0] STATE_IDLE = 3'd1;
  localparam [2:0] STATE_CHECK = 3'd2;
  localparam [2:0] STATE_FILL = 3'd3;
  localparam [2:0] STATE_FILL_WAIT = 3'd4;
  localparam [2:0] STATE_WRITE = 3'd5;

  localparam DEPTH = 1 << INDEX_WIDTH;
  localparam ENTRY_WIDTH = TAG_WIDTH + 66;
  localparam OUT_TAG_WIDTH = OUT_ADDR_WIDTH - INDEX_WIDTH - 3;
  localparam IN_BYTES = IN_DATA_WIDTH / 8;
  localparam IN_BYTE_SHIFT =
    (IN_BYTES <= 1) ? 0 :
    (IN_BYTES <= 2) ? 1 :
    (IN_BYTES <= 4) ? 2 : 3;

  reg  [2:0]                state_reg;
  reg  [INDEX_WIDTH-1:0]    init_counter;
  reg  [1:0]                burst_counter;
  reg  [2:0]                offset_reg;
  reg                       request_rd;
  reg  [TAG_WIDTH-1:0]      request_tag;
  reg  [INDEX_WIDTH-1:0]    request_index;
  reg  [1:0]                request_offset;
  reg  [IN_DATA_WIDTH-1:0]  dout_reg;
  reg                       valid_reg;
  reg  [DEPTH-1:0]          lru_reg;
  reg                       way_reg;
  reg                       entry_valid;
  reg                       entry_dirty;
  reg  [TAG_WIDTH-1:0]      entry_tag;
  reg  [15:0]               entry_word_0;
  reg  [15:0]               entry_word_1;
  reg  [15:0]               entry_word_2;
  reg  [15:0]               entry_word_3;

  wire [INDEX_WIDTH-1:0] current_index = io_in_addr[INDEX_WIDTH+2:3];
  wire [TAG_WIDTH-1:0]   current_tag = {1'b0, io_in_addr[IN_ADDR_WIDTH-1:INDEX_WIDTH+3]};
  wire [2:0]             current_offset = io_in_addr[2:0] >> IN_BYTE_SHIFT;

  wire                   state_init = state_reg == STATE_INIT;
  wire                   state_idle = state_reg == STATE_IDLE;
  wire                   state_check = state_reg == STATE_CHECK;
  wire                   state_fill_wait = state_reg == STATE_FILL_WAIT;
  wire                   state_write = state_reg == STATE_WRITE;
  wire                   start = io_enable & io_in_rd & state_idle;
  wire                   fill_word_valid = state_fill_wait & io_out_valid;
  wire [1:0]             fill_word_index = request_offset + burst_counter;
  wire                   fill_word_done =
    (IN_DATA_WIDTH == 64) ? (&burst_counter) : (burst_counter == 2'd0);

  wire [ENTRY_WIDTH-1:0] way_a_data;
  wire [ENTRY_WIDTH-1:0] way_b_data;
  wire                   way_a_valid = way_a_data[ENTRY_WIDTH-1];
  wire                   way_b_valid = way_b_data[ENTRY_WIDTH-1];
  wire                   way_a_dirty = way_a_data[ENTRY_WIDTH-2];
  wire                   way_b_dirty = way_b_data[ENTRY_WIDTH-2];
  wire [TAG_WIDTH-1:0]   way_a_tag = way_a_data[ENTRY_WIDTH-3:64];
  wire [TAG_WIDTH-1:0]   way_b_tag = way_b_data[ENTRY_WIDTH-3:64];
  wire [63:0]            way_a_line = way_a_data[63:0];
  wire [63:0]            way_b_line = way_b_data[63:0];
  wire                   hit_a = way_a_valid & (way_a_tag == request_tag);
  wire                   hit_b = way_b_valid & (way_b_tag == request_tag);
  wire                   hit = hit_a | hit_b;
  wire [DEPTH-1:0]       lru_shifted = lru_reg >> current_index;
  wire                   lru_way = lru_shifted[0];
  wire                   selected_way = (state_check & hit) ? ~hit_a : (start ? lru_way : way_reg);
  wire                   selected_valid = selected_way ? way_b_valid : way_a_valid;
  wire                   selected_dirty = selected_way ? way_b_dirty : way_a_dirty;
  wire [TAG_WIDTH-1:0]   selected_tag = selected_way ? way_b_tag : way_a_tag;
  wire [63:0]            selected_line = selected_way ? way_b_line : way_a_line;

  wire [15:0]            fill_word_0 =
    fill_word_index == 2'd0 ? io_out_dout : entry_word_0;
  wire [15:0]            fill_word_1 =
    fill_word_index == 2'd1 ? io_out_dout : entry_word_1;
  wire [15:0]            fill_word_2 =
    fill_word_index == 2'd2 ? io_out_dout : entry_word_2;
  wire [15:0]            fill_word_3 =
    fill_word_index == 2'd3 ? io_out_dout : entry_word_3;
  wire [63:0]            fill_line = {fill_word_3, fill_word_2, fill_word_1, fill_word_0};
  wire [ENTRY_WIDTH-1:0] write_entry =
    state_write ? {entry_valid, entry_dirty, entry_tag, entry_word_3, entry_word_2, entry_word_1, entry_word_0}
                : {ENTRY_WIDTH{1'b0}};
  wire                   write_way_a = state_init | (state_write & ~way_reg);
  wire                   write_way_b = state_init | (state_write & way_reg);
  wire [OUT_TAG_WIDTH-1:0] output_tag;

  function [IN_DATA_WIDTH-1:0] read_line_data;
    input [63:0] line;
    input [2:0] offset;
    reg [15:0] selected_word;
    begin
      selected_word = 16'h0;
      case (offset[1:0])
        2'd0:
          selected_word = line[15:0];
        2'd1:
          selected_word = line[31:16];
        2'd2:
          selected_word = line[47:32];
        default:
          selected_word = line[63:48];
      endcase

      if (IN_DATA_WIDTH == 8) begin
        case (offset)
          3'd0:
            read_line_data = line[7:0];
          3'd1:
            read_line_data = line[15:8];
          3'd2:
            read_line_data = line[23:16];
          3'd3:
            read_line_data = line[31:24];
          3'd4:
            read_line_data = line[39:32];
          3'd5:
            read_line_data = line[47:40];
          3'd6:
            read_line_data = line[55:48];
          default:
            read_line_data = line[63:56];
        endcase
      end
      else if (IN_DATA_WIDTH == 16) begin
        read_line_data = {selected_word[7:0], selected_word[15:8]};
      end
      else begin
        read_line_data = {
          line[7:0],
          line[15:8],
          line[23:16],
          line[31:24],
          line[39:32],
          line[47:40],
          line[55:48],
          line[63:56]
        };
      end
    end
  endfunction

  function [DEPTH-1:0] set_lru_bit;
    input [DEPTH-1:0] current_lru;
    input [INDEX_WIDTH-1:0] index;
    input value;
    reg [DEPTH-1:0] mask;
    begin
      mask = {{(DEPTH-1){1'b0}}, 1'b1} << index;
      set_lru_bit = value ? (current_lru | mask) : (current_lru & ~mask);
    end
  endfunction

  reg [2:0] next_state;
  always @(*) begin
    case (state_reg)
      STATE_INIT:
        next_state = (&init_counter) ? STATE_IDLE : state_reg;
      STATE_IDLE:
        next_state = start ? STATE_CHECK : state_reg;
      STATE_CHECK:
        next_state = hit ? STATE_IDLE : STATE_FILL;
      STATE_FILL:
        next_state = io_out_wait_n ? STATE_FILL_WAIT : state_reg;
      STATE_FILL_WAIT:
        next_state = (fill_word_valid & (&burst_counter)) ? STATE_WRITE : state_reg;
      STATE_WRITE:
        next_state = STATE_IDLE;
      default:
        next_state = state_reg;
    endcase
  end

  always @(posedge clock) begin
    if (reset) begin
      state_reg <= STATE_INIT;
      init_counter <= {INDEX_WIDTH{1'b0}};
      burst_counter <= 2'd0;
    end
    else begin
      state_reg <= next_state;
      if (state_init)
        init_counter <= init_counter + 1'b1;
      if (fill_word_valid)
        burst_counter <= burst_counter + 2'd1;
    end

    if (start) begin
      offset_reg <= current_offset;
      request_rd <= io_in_rd;
      request_tag <= current_tag;
      request_index <= current_index;
      request_offset <= io_in_addr[2:1];
    end

    if (state_check & hit)
      dout_reg <= hit_a ? read_line_data(way_a_line, offset_reg) : read_line_data(way_b_line, offset_reg);
    else if (fill_word_valid)
      dout_reg <= read_line_data(fill_line, offset_reg);

    valid_reg <= (state_check & hit) | (fill_word_valid & request_rd & fill_word_done);

    if (state_check) begin
      lru_reg <= hit ? set_lru_bit(lru_reg, request_index, hit_a)
                     : set_lru_bit(lru_reg, request_index, ~way_reg);
      entry_valid <= selected_valid;
      entry_dirty <= selected_dirty;
      entry_tag <= selected_tag;
      entry_word_0 <= selected_line[15:0];
      entry_word_1 <= selected_line[31:16];
      entry_word_2 <= selected_line[47:32];
      entry_word_3 <= selected_line[63:48];
    end
    else if (fill_word_valid) begin
      entry_valid <= 1'b1;
      entry_tag <= request_tag;
      if (fill_word_index == 2'd0)
        entry_word_0 <= io_out_dout;
      if (fill_word_index == 2'd1)
        entry_word_1 <= io_out_dout;
      if (fill_word_index == 2'd2)
        entry_word_2 <= io_out_dout;
      if (fill_word_index == 2'd3)
        entry_word_3 <= io_out_dout;
    end

    if (state_check & hit)
      way_reg <= ~hit_a;
    else if (start)
      way_reg <= lru_way;
  end

  generate
    if (OUT_TAG_WIDTH > TAG_WIDTH) begin : zero_extend_output_tag
      assign output_tag = {{(OUT_TAG_WIDTH-TAG_WIDTH){1'b0}}, request_tag};
    end
    else begin : truncate_output_tag
      assign output_tag = request_tag[OUT_TAG_WIDTH-1:0];
    end
  endgenerate

  CaveSyncReadMem #(
    .ADDR_WIDTH (INDEX_WIDTH),
    .DATA_WIDTH (ENTRY_WIDTH),
    .DEPTH      (DEPTH)
  ) cache_entry_mem_a (
    .read_addr  (current_index),
    .read_en    (1'b1),
    .read_clk   (clock),
    .read_data  (way_a_data),
    .write_addr (request_index),
    .write_en   (write_way_a),
    .write_clk  (clock),
    .write_data (write_entry)
  );

  CaveSyncReadMem #(
    .ADDR_WIDTH (INDEX_WIDTH),
    .DATA_WIDTH (ENTRY_WIDTH),
    .DEPTH      (DEPTH)
  ) cache_entry_mem_b (
    .read_addr  (current_index),
    .read_en    (1'b1),
    .read_clk   (clock),
    .read_data  (way_b_data),
    .write_addr (request_index),
    .write_en   (write_way_b),
    .write_clk  (clock),
    .write_data (write_entry)
  );

  assign io_in_dout = dout_reg;
  assign io_in_wait_n = io_enable & state_idle;
  assign io_in_valid = valid_reg;
  assign io_out_rd = state_reg == STATE_FILL;
  assign io_out_addr = {output_tag, request_index, request_offset, 1'b0};
endmodule
