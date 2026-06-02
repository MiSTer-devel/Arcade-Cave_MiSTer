// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

// One 16-bit register exposed as a tiny memory-mapped register file.
module CaveSingleRegisterFile (
  input         clock,
  input         io_mem_wr,
  input  [1:0]  io_mem_addr,
  input  [15:0] io_mem_din,
  output [15:0] io_regs_0
);
  reg [15:0] regs_0;

  always @(posedge clock) begin
    if (io_mem_wr && io_mem_addr == 2'h0)
      regs_0 <= io_mem_din;
  end

  assign io_regs_0 = regs_0;
endmodule

// Eight 16-bit memory-mapped registers with byte write masks.
module CaveControlRegisterFile (
  input         clock,
  input         io_mem_wr,
  input  [2:0]  io_mem_addr,
  input  [1:0]  io_mem_mask,
  input  [15:0] io_mem_din,
  output [15:0] io_regs_0,
  output [15:0] io_regs_1,
  output [15:0] io_regs_2,
  output [15:0] io_regs_3,
  output [15:0] io_regs_4,
  output [15:0] io_regs_5
);
  reg [15:0] regs_0;
  reg [15:0] regs_1;
  reg [15:0] regs_2;
  reg [15:0] regs_3;
  reg [15:0] regs_4;
  reg [15:0] regs_5;
  reg [15:0] regs_6;
  reg [15:0] regs_7;

  function [15:0] apply_mask;
    input [15:0] old_value;
    input [15:0] new_value;
    input [1:0]  mask;
    begin
      apply_mask = {
        mask[1] ? new_value[15:8] : old_value[15:8],
        mask[0] ? new_value[7:0]  : old_value[7:0]
      };
    end
  endfunction

  always @(posedge clock) begin
    if (io_mem_wr) begin
      case (io_mem_addr)
        3'h0: regs_0 <= apply_mask(regs_0, io_mem_din, io_mem_mask);
        3'h1: regs_1 <= apply_mask(regs_1, io_mem_din, io_mem_mask);
        3'h2: regs_2 <= apply_mask(regs_2, io_mem_din, io_mem_mask);
        3'h3: regs_3 <= apply_mask(regs_3, io_mem_din, io_mem_mask);
        3'h4: regs_4 <= apply_mask(regs_4, io_mem_din, io_mem_mask);
        3'h5: regs_5 <= apply_mask(regs_5, io_mem_din, io_mem_mask);
        3'h6: regs_6 <= apply_mask(regs_6, io_mem_din, io_mem_mask);
        3'h7: regs_7 <= apply_mask(regs_7, io_mem_din, io_mem_mask);
      endcase
    end
  end

  assign io_regs_0 = regs_0;
  assign io_regs_1 = regs_1;
  assign io_regs_2 = regs_2;
  assign io_regs_3 = regs_3;
  assign io_regs_4 = regs_4;
  assign io_regs_5 = regs_5;
endmodule

// Three visible 16-bit memory-mapped registers with byte write masks.
module CaveLayerRegisterFile (
  input         clock,
  input         io_mem_wr,
  input  [1:0]  io_mem_addr,
  input  [1:0]  io_mem_mask,
  input  [15:0] io_mem_din,
  output [15:0] io_mem_dout,
  output [15:0] io_regs_0,
  output [15:0] io_regs_1,
  output [15:0] io_regs_2
);
  reg [15:0] regs_0;
  reg [15:0] regs_1;
  reg [15:0] regs_2;

  reg [15:0] selected_reg;

  function [15:0] apply_mask;
    input [15:0] old_value;
    input [15:0] new_value;
    input [1:0]  mask;
    begin
      apply_mask = {
        mask[1] ? new_value[15:8] : old_value[15:8],
        mask[0] ? new_value[7:0]  : old_value[7:0]
      };
    end
  endfunction

  always @(*) begin
    case (io_mem_addr)
      2'h0: selected_reg = regs_0;
      2'h1: selected_reg = regs_1;
      2'h2: selected_reg = regs_2;
      default: selected_reg = regs_0;
    endcase
  end

  always @(posedge clock) begin
    if (io_mem_wr) begin
      case (io_mem_addr)
        2'h0: regs_0 <= apply_mask(regs_0, io_mem_din, io_mem_mask);
        2'h1: regs_1 <= apply_mask(regs_1, io_mem_din, io_mem_mask);
        2'h2: regs_2 <= apply_mask(regs_2, io_mem_din, io_mem_mask);
        default: begin
        end
      endcase
    end
  end

  assign io_mem_dout = selected_reg;
  assign io_regs_0 = regs_0;
  assign io_regs_1 = regs_1;
  assign io_regs_2 = regs_2;
endmodule
