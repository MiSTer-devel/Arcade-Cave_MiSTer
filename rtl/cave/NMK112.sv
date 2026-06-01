// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

module NMK112(
  input         clock,
  input         io_cpu_wr,
  input  [22:0] io_cpu_addr,
  input  [15:0] io_cpu_din,
  input  [24:0] io_addr_0_in,
  output [24:0] io_addr_0_out,
  input  [24:0] io_addr_1_in,
  output [24:0] io_addr_1_out
);
  reg [4:0] page_0_0;
  reg [4:0] page_0_1;
  reg [4:0] page_0_2;
  reg [4:0] page_0_3;
  reg [4:0] page_1_0;
  reg [4:0] page_1_1;
  reg [4:0] page_1_2;
  reg [4:0] page_1_3;

  wire [1:0] cpu_bank = io_cpu_addr[1:0];
  wire       cpu_chip = io_cpu_addr[2];
  wire [4:0] cpu_page = io_cpu_din[4:0];

  always @(posedge clock) begin
    if (io_cpu_wr) begin
      if (!cpu_chip) begin
        case (cpu_bank)
          2'd0: page_0_0 <= cpu_page;
          2'd1: page_0_1 <= cpu_page;
          2'd2: page_0_2 <= cpu_page;
          default: page_0_3 <= cpu_page;
        endcase
      end
      else begin
        case (cpu_bank)
          2'd0: page_1_0 <= cpu_page;
          2'd1: page_1_1 <= cpu_page;
          2'd2: page_1_2 <= cpu_page;
          default: page_1_3 <= cpu_page;
        endcase
      end
    end
  end

  function [4:0] select_page;
    input [1:0] bank;
    input [4:0] page_0;
    input [4:0] page_1;
    input [4:0] page_2;
    input [4:0] page_3;
    begin
      case (bank)
        2'd0: select_page = page_0;
        2'd1: select_page = page_1;
        2'd2: select_page = page_2;
        default: select_page = page_3;
      endcase
    end
  endfunction

  wire [1:0] chip_0_bank = io_addr_0_in[17:16];
  wire [1:0] chip_1_bank = (io_addr_1_in > 25'h00400) ? io_addr_1_in[17:16]
                                                       : io_addr_1_in[9:8];
  wire [4:0] chip_0_page = select_page(chip_0_bank, page_0_0, page_0_1, page_0_2, page_0_3);
  wire [4:0] chip_1_page = select_page(chip_1_bank, page_1_0, page_1_1, page_1_2, page_1_3);

  assign io_addr_0_out = {4'h0, chip_0_page, io_addr_0_in[15:0]};
  assign io_addr_1_out = {4'h0, chip_1_page, io_addr_1_in[15:0]};
endmodule
