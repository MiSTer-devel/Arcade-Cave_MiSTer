// ============================================================================
//
//                f2sdram_safe_terminator for MiSTer platform
//
// ============================================================================
// Copyright (c) 2021 bellwood420
//
// Background:
//
//   Terminating a transaction of burst writing(/reading) in its midstream
//   seems to cause an illegal state to f2sdram interface.
//
//   Forced reset request that occurs when loading other core is inevitable.
//
//   So if it happens exactly within the transaction period,
//   unexpected issues with accessing to f2sdram interface will be caused
//   in next loaded core.
//
//   It seems that only way to reset broken f2sdram interface is to reset
//   whole SDRAM Controller Subsystem from HPS via permodrst register
//   in Reset Manager.
//   But it cannot be done safely while Linux is running.
//   It is usually done when cold or warm reset is issued in HPS.
//
//   Main_MiSTer is issuing reset for FPGA <> HPS bridges
//   via brgmodrst register in Reset Manager when loading rbf.
//   But it has no effect on f2sdram interface.
//   f2sdram interface seems to belong to SDRAM Controller Subsystem
//   rather than FPGA-to-HPS bridge.
//
//   Main_MiSTer is also trying to issuing reset for f2sdram ports
//   via fpgaportrst register in SDRAM Controller Subsystem when loading rbf.
//   But according to the Intel's document, fpgaportrst register can be
//   used to strech the port reset.
//   It seems that it cannot be used to assert the port reset.
//
//   According to the Intel's document, there seems to be a reset port on
//   Avalon-MM slave interface, but it cannot be found in Qsys generated HDL.
//
//   To conclude, the only thing FPGA can do is not to break the transaction.
//   ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
//
// Purpose:
//   To prevent the issue, this module completes ongoing transaction
//   on behalf of user logic, when reset is asserted.
//
// Usage:
//   Insert this module into the bus line between
//   f2sdram (Avalon-MM slave) and user logic (Avalon-MM master).
//
// Notice:
//   Asynchronous reset request is not supported.
//   Please feed reset request synchronized to clock.
//
module f2sdram_safe_terminator #(
  parameter     ADDRESS_WITDH = 29,
  parameter     DATA_WIDTH = 64,
  parameter     BURSTCOUNT_WIDTH = 8,
  parameter     BYTEENABLE_WIDTH = 8
) (
  // clk should be the same as one provided to f2sdram port
  input         clk,
  // rst_req_sync should be synchronized to clk
  // Asynchronous reset request is not supported
  input         rst_req_sync,

  // Master port: connecting to Alavon-MM slave(f2sdram)
  input                         waitrequest_master,
  output [BURSTCOUNT_WIDTH-1:0] burstcount_master,
  output    [ADDRESS_WITDH-1:0] address_master,
  input        [DATA_WIDTH-1:0] readdata_master,
  input                         readdatavalid_master,
  output                        read_master,
  output       [DATA_WIDTH-1:0] writedata_master,
  output [BYTEENABLE_WIDTH-1:0] byteenable_master,
  output                        write_master,

  // Slave port: connecting to Alavon-MM master(user logic)
  output                        waitrequest_slave,
  input  [BURSTCOUNT_WIDTH-1:0] burstcount_slave,
  input     [ADDRESS_WITDH-1:0] address_slave,
  output       [DATA_WIDTH-1:0] readdata_slave,
  output                        readdatavalid_slave,
  input                         read_slave,
  input        [DATA_WIDTH-1:0] writedata_slave,
  input  [BYTEENABLE_WIDTH-1:0] byteenable_slave,
  input                         write_slave
);
  /*
   * Burst transaction observer
   */
  typedef enum reg [1:0] {
    IDLE, READ, WRITE
  } state_t;

  state_t state = IDLE;
  state_t next_state;

  wire burst_start = state == IDLE  && (next_state == READ || next_state == WRITE);
  wire read_data   = state == READ  && readdatavalid_master;
  wire write_data  = state == WRITE && !waitrequest_master;
  wire burst_end   = (state == READ || state == WRITE) && burstcounter == burstcount_latch - 'd1;
  wire successful_non_burst_write = state == IDLE && write_slave && burstcount_slave == 'd1 && !waitrequest_master; 

  reg [BURSTCOUNT_WIDTH-1:0] burstcounter       = 'd0;
  reg [BURSTCOUNT_WIDTH-1:0] burstcount_latch   = 'd0;
  reg [ADDRESS_WITDH-1:0]    address_latch      = 'd0;
  reg [BYTEENABLE_WIDTH-1:0] byteenable_latch   = 'd0;
  reg read_latch  = 1'b0;
  reg write_latch = 1'b0;

  always_ff @(posedge clk) begin
    state <= next_state;

    if (burst_start) begin
      if (next_state == WRITE) begin
        burstcounter <= waitrequest_master ? 'd0 :'d1;
      end else if (next_state == READ) begin
        burstcounter <= 'd0;
      end

      burstcount_latch <= burstcount_slave;
      byteenable_latch <= byteenable_slave;
      address_latch    <= address_slave;
      read_latch       <= next_state == READ;
      write_latch      <= next_state == WRITE;

    end else if (read_data || write_data) begin
      burstcounter <= burstcounter + 'd1;

    end else if (burst_end) begin
      read_latch  <= 1'b0;
      write_latch <= 1'b0;
    end
  end

  always_comb begin
    case (state)
      IDLE:     if (successful_non_burst_write)
                  next_state <= IDLE;
                else if (read_slave)
                  next_state <= READ;
                else if (write_slave)
                  next_state <= WRITE;
                else
                  next_state <= IDLE;

      READ:     if (burst_end)
                  next_state <= IDLE;
                else
                  next_state <= READ;

      WRITE:    if (burst_end)
                  next_state <= IDLE;
                else
                  next_state <= WRITE;

      default:  next_state <= IDLE;
    endcase
  end

  /*
   * Safe terminating
   */
  wire on_transaction = (state == READ || state == WRITE) && (next_state != IDLE);
  reg terminating = 1'b0;
  reg terminated = 1'b0;

  reg [BURSTCOUNT_WIDTH-1:0] terminate_counter = 'd0;
  reg [BURSTCOUNT_WIDTH-1:0] terminate_count = 'd0;
  reg [ADDRESS_WITDH-1:0]    terminate_address_latch      = 'd0;
  reg [BYTEENABLE_WIDTH-1:0] terminate_byteenable_latch   = 'd0;
  reg terminate_read_latch  = 1'b0;
  reg terminate_write_latch = 1'b0;

  reg init_reset_deasserted = 1'b0;

  always_ff @(posedge clk) begin
    if (rst_req_sync) begin
      // Reset assert
      if (init_reset_deasserted) begin
        if (on_transaction) begin
          terminating <= 1'b1;
          terminate_counter          <= burstcounter + 'd1;
          terminate_count            <= burstcount_latch;
          terminate_address_latch    <= address_latch;
          terminate_byteenable_latch <= byteenable_latch;
          terminate_read_latch       <= read_latch;
          terminate_write_latch      <= write_latch;
        end else begin
          terminated = 1'b1;
        end
      end
    end else begin
      // Reset deassert
      if (!terminating) begin
        terminated <= 1'b0;
      end
      init_reset_deasserted <= 1'b1;
    end

    if (terminating) begin
      // Continue read/write transaction until the end

      if (terminate_read_latch && readdatavalid_master) begin
        terminate_counter <= terminate_counter + 'd1;
      end else if (terminate_write_latch && !waitrequest_master) begin
        terminate_counter <= terminate_counter + 'd1;
      end

      if (terminate_counter == terminate_count - 'd1) begin
        terminating <= 1'b0;
        terminated <= 1'b1;
      end
    end
  end

  /*
   * Bus mux depending on the stage.
   */
  always_comb begin
    if (terminated) begin
      burstcount_master = 'd1;
      address_master    = 'd0;
      read_master       = 'b0;
      writedata_master  = 'd0;
      byteenable_master = 'd0;
      write_master      = 'b0;
    end else if (terminating) begin
      burstcount_master = burstcount_latch;
      address_master    = terminate_address_latch;
      read_master       = read_latch;
      writedata_master  = 'd0;
      byteenable_master = terminate_byteenable_latch;
      write_master      = write_latch;
    end else begin
      burstcount_master = burstcount_slave;
      address_master    = address_slave;
      read_master       = read_slave;
      writedata_master  = writedata_slave;
      byteenable_master = byteenable_slave;
      write_master      = write_slave;
    end
  end

  // Just passing through master to slave
  assign waitrequest_slave   = waitrequest_master;
  assign readdata_slave      = readdata_master;
  assign readdatavalid_slave = readdatavalid_master;

endmodule
