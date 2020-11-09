-------------------------------------------------------------------------------
--
-- Copyright (c) 2019 Rick Wertenbroek <rick.wertenbroek@gmail.com>
-- All rights reserved.
--
-- Redistribution and use in source and binary forms, with or without
-- modification, are permitted provided that the following conditions are met:
--
-- 1. Redistributions of source code must retain the above copyright notice,
-- this list of conditions and the following disclaimer.
--
-- 2. Redistributions in binary form must reproduce the above copyright notice,
-- this list of conditions and the following disclaimer in the documentation
-- and/or other materials provided with the distribution.
--
-- 3. Neither the name of the copyright holder nor the names of its
-- contributors may be used to endorse or promote products derived from this
-- software without specific prior written permission.
--
-- THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
-- AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
-- IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
-- ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
-- LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
-- CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
-- SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
-- INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
-- CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
-- ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
-- POSSIBILITY OF SUCH DAMAGE.
--
-------------------------------------------------------------------------------
-- File         : ddr3_arbiter.vhd
-- Description  : Receives requests for burst reads / writes to the DDR3 from
--                multiple sources. Accesses will be handled in a fair way so
--                that every source can get access to the DDR3. The requests
--                are specific to each source and handled separately, the DDR3
--                interface is an Avalon bus.
--
-- Author       : Rick Wertenbroek
-- Version      : 0.0
--
-- VHDL std     : 2008
-- Dependencies : log_pkg.vhd
-------------------------------------------------------------------------------

library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

use work.log_pkg.all;

entity ddr3_arbiter is
    generic (
        C_DDR3_ADDRESS_WIDTH   : integer := 32;
        C_DDR3_DATA_WIDTH      : integer := 64;
        C_DDR3_CACHE_BURST_LEN : integer := 4  -- TODO Check, Cache line is
                                               -- 16x16bit words i.e., 255 bits
        );
    port (
        -- Global
        ------------
        clock_i                    : in  std_logic;
        reset_i                    : in  std_logic;
        -- DDR3 interface (Avalon)
        -----------------------------
        -- Common
        ddr3_address_o             : out unsigned(C_DDR3_ADDRESS_WIDTH-1 downto 0);
        ddr3_waitrequest_i         : in  std_logic;
        ddr3_burstcount_o          : out std_logic_vector(7 downto 0);
        -- Read
        ddr3_read_o                : out std_logic;
        ddr3_read_data_valid_i     : in  std_logic;
        ddr3_read_data_i           : in  std_logic_vector(C_DDR3_DATA_WIDTH-1 downto 0);
        -- Write
        ddr3_write_o               : out std_logic;
        ddr3_write_data_o          : out std_logic_vector(C_DDR3_DATA_WIDTH-1 downto 0);
        ddr3_write_be_o            : out std_logic_vector(7 downto 0);
        --
        -- IOCTL Download port
        ------------------------------
        ioctl_download_i           : in  std_logic;
        ioctl_address_i            : in  unsigned(C_DDR3_ADDRESS_WIDTH-1 downto 0);
        ioctl_write_i              : in  std_logic;
        ioctl_data_i               : in  std_logic_vector(7 downto 0);
        ioctl_wait_o               : out std_logic;
        --
        -- Cache Memory Access Port
        ------------------------------
        cache_address_i            : in  unsigned(C_DDR3_ADDRESS_WIDTH-1 downto 0);
        cache_read_i               : in  std_logic;
        cache_data_valid_o         : out std_logic;
        cache_data_o               : out std_logic_vector(C_DDR3_DATA_WIDTH*C_DDR3_CACHE_BURST_LEN-1 downto 0);
        -- Graphic Tile Access Port
        ------------------------------
        gfx_address_i              : in  unsigned(C_DDR3_ADDRESS_WIDTH-1 downto 0);
        gfx_tiny_burst_i           : in  std_logic;
        gfx_burst_read_i           : in  std_logic;
        gfx_data_o                 : out std_logic_vector(C_DDR3_DATA_WIDTH-1 downto 0);
        gfx_data_valid_o           : out std_logic;
        gfx_burst_read_done_o      : out std_logic;
        -- Frame Buffer Access Port
        ------------------------------
        -- Write
        fb_to_ddr3_address_i       : in  unsigned(C_DDR3_ADDRESS_WIDTH-1 downto 0);
        -- Burst count must remain stable through the burst !
        fb_to_ddr3_burstcount_i    : in  std_logic_vector(7 downto 0);
        fb_to_ddr3_write_i         : in  std_logic;
        fb_to_ddr3_writedata_i     : in  std_logic_vector(C_DDR3_DATA_WIDTH-1 downto 0);
        fb_to_ddr3_waitrequest_o   : out std_logic;
        -- Read
        fb_from_ddr3_address_i     : in  unsigned(C_DDR3_ADDRESS_WIDTH-1 downto 0);
        -- Burst count must remain stable through the burst !
        fb_from_ddr3_burstcount_i  : in  std_logic_vector(7 downto 0);
        fb_from_ddr3_read_i        : in  std_logic;
        fb_from_ddr3_waitrequest_o : out std_logic;
        fb_from_ddr3_data_o        : out std_logic_vector(C_DDR3_DATA_WIDTH-1 downto 0);
        fb_from_ddr3_data_valid_o  : out std_logic);
end ddr3_arbiter;

architecture rtl of ddr3_arbiter is

    -----------
    -- Types --
    -----------
    type state_t is (IDLE, CHECK_RR1, CHECK_RR2, CHECK_RR3, CHECK_RR4, SEND_CACHE_REQ, SEND_GFX_REQ, WAIT_CACHE_REQ, WAIT_GFX_REQ, FB_TO_DDR, FB_FROM_DDR, IOCTL_TRANSFER);

    -------------
    -- Signals --
    -------------

    -- Request registers
    signal cache_request_reg_s       : std_logic;
    signal cache_request_addr_reg_s  : unsigned(C_DDR3_ADDRESS_WIDTH-1 downto 0);
    --
    signal gfx_request_reg_s         : std_logic;
    signal gfx_request_addr_reg_s    : unsigned(C_DDR3_ADDRESS_WIDTH-1 downto 0);
    signal gfx_request_tiny_reg_s    : std_logic;

    -- State Machine
    signal state_reg_s, state_next_s : state_t;

    signal cache_burst_read_done_s   : std_logic;
    signal gfx_burst_read_done_s     : std_logic;
    signal gfx_burst_size_s          : unsigned(7 downto 0);

    signal rr_counter_s              : unsigned(1 downto 0);

    -- FB to DDR
    signal fb_to_ddr3_burst_done_s   : std_logic;
    -- DDR to FB
    signal fb_from_ddr3_burst_done_s : std_logic;

    signal ioctl_data_x8_s : std_logic_vector(C_DDR3_DATA_WIDTH-1 downto 0);
    signal ioctl_be_s      : std_logic_vector(7 downto 0);
begin

    -----------------------
    -- Request Registers --
    -----------------------

    -- Register a cache request, remove it when done
    cache_request_registers_process : process(clock_i) is
    begin
        if rising_edge(clock_i) then
            if reset_i = '1' then
                cache_request_reg_s <= '0';
            else
                if cache_read_i = '1' then
                    cache_request_reg_s      <= '1';
                    cache_request_addr_reg_s <= cache_address_i;
                elsif cache_burst_read_done_s = '1' then
                    cache_request_reg_s      <= '0';
                end if;
            end if;
        end if;
    end process cache_request_registers_process;

    -- Register a gfx request, remove it when done
    gfx_request_registers_process : process(clock_i) is
    begin
        if rising_edge(clock_i) then
            if reset_i = '1' then
                gfx_request_reg_s <= '0';
            else
                if gfx_burst_read_i = '1' then
                    gfx_request_reg_s      <= '1';
                    gfx_request_addr_reg_s <= gfx_address_i;
                    gfx_request_tiny_reg_s <= gfx_tiny_burst_i;
                elsif gfx_burst_read_done_s = '1' then
                    gfx_request_reg_s      <= '0';
                end if;
            end if;
        end if;
    end process gfx_request_registers_process;

    -- Register a video request, remove it when done
    video_request_registers_process : process(clock_i) is
    begin
        if rising_edge(clock_i) then
            if reset_i = '1' then
                null; -- TODO
            end if;
        end if;
    end process video_request_registers_process;

    -------------------------
    -- Round-Robin Counter --
    -------------------------
    round_robin_counter_process : process(clock_i) is
    begin
        if rising_edge(clock_i) then
            if reset_i = '1' then
                rr_counter_s <= (others => '0');
            else
                -- Update round-robin counter
                if (state_reg_s = IDLE) and (state_next_s /= IDLE) then
                    rr_counter_s <= rr_counter_s + 1;
                end if; -- Update
            end if; -- Reset
        end if; -- Rising Edge Clock
    end process round_robin_counter_process;

    -------------------
    -- State Machine --
    -------------------
    fsm_state_register_process : process(clock_i) is
    begin
        if rising_edge(clock_i) then
            if reset_i = '1' then
                state_reg_s <= IDLE;
            else
                state_reg_s <= state_next_s;
            end if; -- Reset
        end if; -- Rising Edge Clock
    end process fsm_state_register_process;

    fsm_next_state_process : process(all) is
    begin
        -- Default behavior, stay in the same state
        state_next_s <= state_reg_s;

        case state_reg_s is
            when IDLE =>
                -- Round-Robin arbitration
                case to_integer(rr_counter_s) is
                    when 0 =>
                        state_next_s <= CHECK_RR1;
                    when 1 =>
                        state_next_s <= CHECK_RR2;
                    when 2 =>
                        state_next_s <= CHECK_RR3;
                    -- If the counter max value if bigger than the number above
                    -- then simply the first priority check gets used for the
                    -- remaining checks until the counter overflows.
                    when others =>
                        state_next_s <= CHECK_RR4;
                end case;

            -- TODO : The CHECK_RR states will check the requests in another
            -- order each. This could be done better but it does not really
            -- matter. (E.g., combinatorial RR priority encoder) however they
            -- are both functionnally equivalent.
            -- FF_FROM_DDR3 bypasses everyone else
            when CHECK_RR1 =>
                if fb_from_ddr3_read_i = '1' then -- DDR->FB
                    state_next_s <= FB_FROM_DDR;
                elsif ioctl_download_i = '1' then
                    state_next_s <= IOCTL_TRANSFER;
                elsif cache_request_reg_s = '1' then -- Cache
                    state_next_s <= SEND_CACHE_REQ;
                elsif gfx_request_reg_s = '1' then -- GFX
                    state_next_s <= SEND_GFX_REQ;
                elsif fb_to_ddr3_write_i = '1' then -- FB->DDR
                    state_next_s <= FB_TO_DDR;
                end if;

            when CHECK_RR2 =>
                if fb_from_ddr3_read_i = '1' then -- DDR->FB
                    state_next_s <= FB_FROM_DDR;
                elsif ioctl_download_i = '1' then
                    state_next_s <= IOCTL_TRANSFER;
                elsif gfx_request_reg_s = '1' then -- GFX
                    state_next_s <= SEND_GFX_REQ;
                elsif fb_to_ddr3_write_i = '1' then -- FB->DDR
                    state_next_s <= FB_TO_DDR;
                elsif cache_request_reg_s = '1' then -- Cache
                    state_next_s <= SEND_CACHE_REQ;
                end if;

            when CHECK_RR3 =>
                if fb_from_ddr3_read_i = '1' then -- DDR->FB
                    state_next_s <= FB_FROM_DDR;
                elsif ioctl_download_i = '1' then
                    state_next_s <= IOCTL_TRANSFER;
                elsif fb_to_ddr3_write_i = '1' then -- FB->DDR
                    state_next_s <= FB_TO_DDR;
                elsif gfx_request_reg_s = '1' then -- GFX
                    state_next_s <= SEND_GFX_REQ;
                elsif cache_request_reg_s = '1' then -- Cache
                    state_next_s <= SEND_CACHE_REQ;
                end if;

            when CHECK_RR4 =>
                if fb_from_ddr3_read_i = '1' then -- DDR->FB
                    state_next_s <= FB_FROM_DDR;
                elsif ioctl_download_i = '1' then
                    state_next_s <= IOCTL_TRANSFER;
                elsif gfx_request_reg_s = '1' then -- GFX
                    state_next_s <= SEND_GFX_REQ;
                elsif cache_request_reg_s = '1' then -- Cache
                    state_next_s <= SEND_CACHE_REQ;
                elsif fb_to_ddr3_write_i = '1' then -- FB->DDR
                    state_next_s <= FB_TO_DDR;
                end if;


            when SEND_CACHE_REQ =>
                if ddr3_waitrequest_i = '0' then
                    state_next_s <= WAIT_CACHE_REQ;
                end if;

            when WAIT_CACHE_REQ =>
                if cache_burst_read_done_s = '1' then
                    state_next_s <= IDLE;
                end if;

            when SEND_GFX_REQ =>
                if ddr3_waitrequest_i = '0' then
                    state_next_s <= WAIT_GFX_REQ;
                end if;

            when WAIT_GFX_REQ =>
                if gfx_burst_read_done_s = '1' then
                    state_next_s <= IDLE;
                end if;

            when FB_TO_DDR =>
                if fb_to_ddr3_burst_done_s = '1' then
                    state_next_s <= IDLE;
                end if;

            when FB_FROM_DDR =>
                if fb_from_ddr3_burst_done_s = '1' then
                    state_next_s <= IDLE;
                end if;

            when IOCTL_TRANSFER =>
                --if (ioctl_write_i = '1') and (ddr3_waitrequest_i = '0') then
                if (ioctl_download_i = '0') then
                    state_next_s <= IDLE;
                end if;

            when others =>
                null;

        end case;

    end process fsm_next_state_process;

    --------------------
    -- IOCTL Transfer --
    --------------------
    ioctl_block : block
    begin
        ioctl_data_x8_s <= ioctl_data_i & ioctl_data_i & ioctl_data_i & ioctl_data_i &
                           ioctl_data_i & ioctl_data_i & ioctl_data_i & ioctl_data_i;
        ioctl_be_s   <= std_logic_vector(shift_left(to_unsigned(1, 8), to_integer(ioctl_address_i(2 downto 0))));
        ioctl_wait_o <= ioctl_write_i and ddr3_waitrequest_i when state_reg_s = IOCTL_TRANSFER else
                        ioctl_write_i;
    end block ioctl_block;

    ----------------------------
    -- Cache request handling --
    ----------------------------
    cache_block : block
        subtype data_word_t is std_logic_vector(C_DDR3_DATA_WIDTH-1 downto 0);
        type data_word_array_t is array (natural range <>) of data_word_t;

        signal cache_shift_register_s  : data_word_array_t(C_DDR3_CACHE_BURST_LEN-1 downto 0);
        signal cache_shift_counter_s   : unsigned(ilogup(cache_shift_register_s'length) downto 0);
    begin

        -- Shift register
        shift_register_process : process(clock_i) is
        begin
            if rising_edge(clock_i) then
                if ddr3_read_data_valid_i = '1' then
                    cache_shift_register_s(cache_shift_register_s'high) <= ddr3_read_data_i;
                    for i in 0 to cache_shift_register_s'high-1 loop
                        cache_shift_register_s(i) <= cache_shift_register_s(i+1);
                    end loop;
                end if;
            end if;
        end process shift_register_process;

        -- Counter
        cache_shift_counter_process : process(clock_i) is
        begin
            if rising_edge(clock_i) then
                -- Note : The IDLE reset could be removed
                if (state_reg_s = IDLE) or (state_reg_s = SEND_CACHE_REQ) then
                    cache_shift_counter_s   <= (others => '0');
                    cache_burst_read_done_s <= '0';
                else
                    if state_reg_s = WAIT_CACHE_REQ then
                        if ddr3_read_data_valid_i = '1' then
                            cache_shift_counter_s <= cache_shift_counter_s + 1;
                            if cache_shift_counter_s = C_DDR3_CACHE_BURST_LEN-1 then
                                cache_burst_read_done_s <= '1';
                            else
                                cache_burst_read_done_s <= '0';
                            end if; -- Done condition
                        end if; -- Update condition
                    end if; -- State is cache related
                end if; -- Reset (in IDLE state)
            end if; -- Rising Edge Clock
        end process cache_shift_counter_process;

        -------------
        -- Outputs --
        -------------
        cache_data_valid_o <= cache_burst_read_done_s;
        process (cache_shift_register_s) is
        begin
            for i in 0 to cache_shift_register_s'high loop
                cache_data_o((i+1)*C_DDR3_DATA_WIDTH-1 downto i*C_DDR3_DATA_WIDTH) <= cache_shift_register_s(i);
            end loop;
        end process;

    end block cache_block;

    --------------------------
    -- GFX Request handling --
    --------------------------
    gfx_block : block
        signal gfx_counter_s     : unsigned(4 downto 0);
        signal tiny_burst_done_s : std_logic;
        signal burst_done_s      : std_logic;
    begin
        -- Counter
        -- Tiny burst is 16*32bit, normal burst is 32*32bit however the DDR3
        -- interface provided is 64bit this means we will do 8*64bit for tiny
        -- bursts and 16*64bit for the normal one.
        gfx_counter_process : process(clock_i) is
        begin
            if rising_edge(clock_i) then
                -- Note : The IDLE state reset could be removed
                if (state_reg_s = IDLE) or (state_reg_s = SEND_GFX_REQ) then
                    gfx_counter_s <= (others => '0');
                else
                    if (state_reg_s = WAIT_GFX_REQ) and (ddr3_read_data_valid_i = '1') then
                        gfx_counter_s <= gfx_counter_s + 1;
                    end if; -- Update
                end if; -- Reset
            end if; -- Rising Edge Clock
        end process gfx_counter_process;

        -- Note : both signals below could be asserted one clock earlier if needed.
        tiny_burst_done_s <= '1' when gfx_counter_s(3) = '1' else '0';
        burst_done_s      <= '1' when gfx_counter_s(4) = '1' else '0';

        -- Assert the done signal
        gfx_burst_read_done_s <= tiny_burst_done_s when gfx_request_tiny_reg_s = '1' else burst_done_s;

        -- Set GFX burst size to either 8 or 16, depending on the GFX_TINY_BURST flag
        gfx_burst_size_s <= x"08" when gfx_request_tiny_reg_s = '1' else x"10";

        -------------
        -- Outputs --
        -------------
        gfx_burst_read_done_o <= gfx_burst_read_done_s;
        gfx_data_o <= ddr3_read_data_i;
        gfx_data_valid_o <= ddr3_read_data_valid_i when state_reg_s = WAIT_GFX_REQ else '0';
    end block gfx_block;

    ---------------
    -- FB -> DDR --
    ---------------
    fb_to_ddr3_block : block
        signal burst_counter_s      : unsigned(7 downto 0);
        signal burst_counter_next_s : unsigned(7 downto 0);
    begin
        burst_counter_next_s <= burst_counter_s + 1 when (fb_to_ddr3_write_i = '1') and (ddr3_waitrequest_i = '0') else
                                burst_counter_s;

        burst_counter_process : process(clock_i) is
        begin
            if rising_edge(clock_i) then
                -- On entry
                if (state_reg_s /= FB_TO_DDR) and (state_next_s = FB_TO_DDR) then
                    burst_counter_s <= (others => '0');
                else
                    burst_counter_s <= burst_counter_next_s;
                end if;
            end if;
        end process burst_counter_process;

        fb_to_ddr3_burst_done_s <= '1' when burst_counter_next_s = unsigned(fb_to_ddr3_burstcount_i) else
                                   '0';

        fb_to_ddr3_waitrequest_o <= ddr3_waitrequest_i when state_reg_s = FB_TO_DDR else
                                    '1';
    end block fb_to_ddr3_block;

    ---------------
    -- DDR -> FB --
    ---------------
    ddr3_to_video_out_block : block
        signal burst_counter_s      : unsigned(7 downto 0);
        signal burst_counter_next_s : unsigned(7 downto 0);
    begin
        burst_counter_next_s <= burst_counter_s + 1 when (ddr3_read_data_valid_i = '1') and (state_reg_s = FB_FROM_DDR) else
                                burst_counter_s;

        burst_counter_process : process(clock_i) is
        begin
            if rising_edge(clock_i) then
                -- On entry
                if (state_reg_s /= FB_FROM_DDR) and (state_next_s = FB_FROM_DDR) then
                    burst_counter_s <= (others => '0');
                else
                    burst_counter_s <= burst_counter_next_s;
                end if;
            end if;
        end process burst_counter_process;

        fb_from_ddr3_burst_done_s  <= '1' when burst_counter_next_s = unsigned(fb_from_ddr3_burstcount_i) else
                                      '0';
        fb_from_ddr3_waitrequest_o <= ddr3_waitrequest_i when state_reg_s = FB_FROM_DDR else
                                      '1';
        fb_from_ddr3_data_o        <= ddr3_read_data_i;
        fb_from_ddr3_data_valid_o  <= ddr3_read_data_valid_i when state_reg_s = FB_FROM_DDR else
                                      '0';
    end block ddr3_to_video_out_block;

    -------------
    -- Outputs --
    -------------

    -- Common signals to the DDR3
    ddr3_burstcount_o  <= std_logic_vector(to_unsigned(C_DDR3_CACHE_BURST_LEN, ddr3_burstcount_o'length)) when state_reg_s = SEND_CACHE_REQ else
                          std_logic_vector(gfx_burst_size_s)                                              when state_reg_s = SEND_GFX_REQ else
                          fb_to_ddr3_burstcount_i                                                         when state_reg_s = FB_TO_DDR else
                          fb_from_ddr3_burstcount_i                                                       when state_reg_s = FB_FROM_DDR else
                          "00000001";                --when state_reg_s = IOCTL_TRANSFER else
                          --(others => '0');
    ddr3_address_o     <= cache_request_addr_reg_s when state_reg_s = SEND_CACHE_REQ else
                          gfx_request_addr_reg_s   when state_reg_s = SEND_GFX_REQ else
                          fb_to_ddr3_address_i     when state_reg_s = FB_TO_DDR else
                          fb_from_ddr3_address_i   when state_reg_s = FB_FROM_DDR else
                          ioctl_address_i          when state_reg_s = IOCTL_TRANSFER else
                          (others => '0');

    -- Read signals to the DDR3
    ddr3_read_o        <= '1' when (state_reg_s = SEND_CACHE_REQ) or (state_reg_s = SEND_GFX_REQ) else
                          fb_from_ddr3_read_i when state_reg_s = FB_FROM_DDR else
                          '0';

    -- Write signals to the DDR3
    ddr3_write_data_o  <= fb_to_ddr3_writedata_i when state_reg_s = FB_TO_DDR else
                          ioctl_data_x8_s        when state_reg_s = IOCTL_TRANSFER else
                          (others => '0');
    ddr3_write_o       <= fb_to_ddr3_write_i when state_reg_s = FB_TO_DDR else
                          ioctl_write_i      when state_reg_s = IOCTL_TRANSFER else
                          '0';
    ddr3_write_be_o    <= ioctl_be_s when state_reg_s = IOCTL_TRANSFER else
                          (others => '1');
end rtl;
