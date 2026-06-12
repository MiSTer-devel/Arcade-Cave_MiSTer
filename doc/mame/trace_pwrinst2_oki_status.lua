local out_path = os.getenv("MAME_PWRINST2_OKI_STATUS_TRACE_OUT") or "pwrinst2_oki_status_trace.csv"
local trace_seconds = tonumber(os.getenv("MAME_PWRINST2_OKI_STATUS_TRACE_SECONDS") or "18.0") or 18.0
local inject_start_frame = tonumber(os.getenv("MAME_PWRINST2_INJECT_START_FRAME") or "90") or 90
local inject_spacing_frames = tonumber(os.getenv("MAME_PWRINST2_INJECT_SPACING_FRAMES") or "30") or 30
local autostart = os.getenv("MAME_PWRINST2_AUTOSTART") == "1"
local coin_frame = tonumber(os.getenv("MAME_PWRINST2_COIN_FRAME") or "120") or 120
local start_frame = tonumber(os.getenv("MAME_PWRINST2_START_FRAME") or "180") or 180
local input_pulse_frames = tonumber(os.getenv("MAME_PWRINST2_INPUT_PULSE_FRAMES") or "8") or 8
local out = assert(io.open(out_path, "w"))
out:setvbuf("line")

local machine = manager.machine
local maincpu = assert(machine.devices[":maincpu"], "missing :maincpu")
local audiocpu = assert(machine.devices[":audiocpu"], "missing :audiocpu")
local main_space = assert(maincpu.spaces["program"], "missing main program space")
local io_space = assert(audiocpu.spaces["io"], "missing audiocpu io space")
local input_ports = machine.ioport.ports

local frame = 0
local oki_pending = { false, false }
local oki_phrase = { 0, 0 }
local oki_status = { 0, 0 }
local oki_status_reads = { 0, 0 }
local oki_start_events = { 0, 0 }
local oki_stop_events = { 0, 0 }
local oki_overlap_events = { 0, 0 }
local oki_last_overlap = { 0, 0 }
local oki_last_status = { 0, 0 }
local oki_last_start = { 0, 0 }
local last_main_command = 0
local latch_reads = 0
local inject_commands = {}
local inject_index = 1
local inject_next_frame = inject_start_frame

local inject_env = os.getenv("MAME_PWRINST2_INJECT_COMMANDS")
if inject_env then
  for token in string.gmatch(inject_env, "([^,]+)") do
    token = token:gsub("^%s+", ""):gsub("%s+$", "")
    local value
    if token:sub(1, 2) == "0x" or token:sub(1, 2) == "0X" then
      value = tonumber(token:sub(3), 16)
    else
      value = tonumber(token)
    end
    if value then
      inject_commands[#inject_commands + 1] = value & 0xffff
    end
  end
end

local function find_field(port_tag, field_name)
  local port = input_ports[port_tag]
  if port == nil then
    return nil
  end
  for _, field in pairs(port.fields) do
    if field.name == field_name then
      return field
    end
  end
  return nil
end

local coin1_field = find_field(":IN0", "Coin 1")
local start1_field = find_field(":IN0", "1 Player Start")

local function hx(value, digits)
  return string.format("%0" .. digits .. "X", value & ((1 << (digits * 4)) - 1))
end

local function now()
  return machine.time:as_double()
end

local function main_pc()
  return maincpu.state["PC"].value & 0xffffff
end

local function z80_pc()
  return audiocpu.state["PC"].value & 0xffff
end

local function log(kind, detail, addr, data, mask, pc)
  out:write(string.format(
    "%.6f,%d,%s,%s,%s,%s,%s,%s\n",
    now(),
    frame,
    kind,
    detail,
    hx(addr or 0, 6),
    hx(data or 0, 4),
    hx(mask or 0, 4),
    pc or hx(z80_pc(), 4)))
end

local function drain_main_replies(limit)
  for _ = 1, limit do
    local reply = main_space:read_u16(0xd80000) & 0xffff
    log("LUA", "drain_sound_ack", 0xd80000, reply, 0xffff, "mainpc_" .. hx(main_pc(), 6))
    if (reply & 0xff) == 0xff then
      return
    end
  end
end

local function force_in0(data)
  local value = data & 0xffff
  if autostart and frame >= coin_frame and frame < (coin_frame + input_pulse_frames) then
    value = value & ~0x0100
  end
  if autostart and frame >= start_frame and frame < (start_frame + input_pulse_frames) then
    value = value & ~0x0080
  end
  return value
end

local function set_input_field(field, pressed)
  if field == nil then
    return
  end
  if pressed then
    field:set_value(0)
  else
    field:clear_value()
  end
end

local function trace_oki_status(chip, addr, data, mask)
  local index = chip + 1
  local value = data & 0xff
  oki_status[index] = value
  oki_last_status[index] = value
  oki_status_reads[index] = oki_status_reads[index] + 1
  log("OKI" .. chip, "status_" .. hx(value, 2), addr, value, mask)
end

local function trace_oki_write(chip, addr, data, mask)
  local index = chip + 1
  local value = data & 0xff

  -- The OKI command state matters: a second byte can be 0x8x for channel 8.
  if oki_pending[index] then
    local chan = (value >> 4) & 0xf
    local att = value & 0xf
    local status_low = oki_status[index] & 0xf
    local overlap = chan & status_low
    oki_pending[index] = false
    oki_last_start[index] = value
    oki_last_overlap[index] = overlap
    oki_start_events[index] = oki_start_events[index] + 1
    if overlap ~= 0 then
      oki_overlap_events[index] = oki_overlap_events[index] + 1
    end
    log(
      "OKI" .. chip,
      "start_phrase_" .. hx(oki_phrase[index], 2) ..
        "_chan_" .. hx(chan, 1) ..
        "_att_" .. hx(att, 1) ..
        "_status_" .. hx(oki_status[index], 2) ..
        "_overlap_" .. hx(overlap, 1),
      addr,
      value,
      mask)
  elseif value >= 0x80 then
    oki_pending[index] = true
    oki_phrase[index] = value & 0x7f
    log("OKI" .. chip, "phrase_" .. hx(value & 0x7f, 2), addr, value, mask)
  else
    oki_stop_events[index] = oki_stop_events[index] + 1
    log("OKI" .. chip, "stop_" .. hx((value >> 3) & 0xf, 1), addr, value, mask)
  end
end

out:write("time,frame,kind,detail,addr,data,mask,pc\n")
log("TRACE", "start", 0, 0, 0)

emu.register_frame_done(function()
  frame = frame + 1
  local coin_active = autostart and frame >= coin_frame and frame < (coin_frame + input_pulse_frames)
  local start_active = autostart and frame >= start_frame and frame < (start_frame + input_pulse_frames)

  set_input_field(coin1_field, coin_active)
  set_input_field(start1_field, start_active)

  if coin_active then
    log("INPUT", "coin1_set_value", 0, 0, 0)
  end
  if start_active then
    log("INPUT", "start1_set_value", 0, 0, 0)
  end

  if inject_index <= #inject_commands and frame >= inject_next_frame then
    local command = inject_commands[inject_index]
    main_space:write_u16(0xe00000, command)
    last_main_command = command
    log("LUA", "inject_sound_command_" .. hx(command, 4), 0xe00000, command, 0xffff, "mainpc_" .. hx(main_pc(), 6))
    drain_main_replies(8)
    inject_index = inject_index + 1
    inject_next_frame = inject_next_frame + inject_spacing_frames
  end
end, "pwrinst2_oki_status_trace_frame")

main_space:install_write_tap(0xe00000, 0xe00001, "pwrinst2_status_main_sound_cmd_w", function(offset, data, mask)
  last_main_command = data & 0xffff
  log("M68K", "sound_command", offset, data, mask, "mainpc_" .. hx(main_pc(), 6))
end)

main_space:install_read_tap(0x500000, 0x500001, "pwrinst2_status_in0_autostart", function(offset, data, mask)
  local forced = force_in0(data)
  if forced ~= (data & 0xffff) then
    log("INPUT", "force_in0", offset, forced, mask, "mainpc_" .. hx(main_pc(), 6))
    return forced
  end
end)

io_space:install_read_tap(0x00, 0x00, "pwrinst2_oki0_status_r", function(offset, data, mask)
  trace_oki_status(0, offset, data, mask)
end)

io_space:install_read_tap(0x08, 0x08, "pwrinst2_oki1_status_r", function(offset, data, mask)
  trace_oki_status(1, offset, data, mask)
end)

io_space:install_write_tap(0x00, 0x00, "pwrinst2_oki0_status_w", function(offset, data, mask)
  trace_oki_write(0, offset, data, mask)
end)

io_space:install_write_tap(0x08, 0x08, "pwrinst2_oki1_status_w", function(offset, data, mask)
  trace_oki_write(1, offset, data, mask)
end)

io_space:install_read_tap(0x60, 0x70, "pwrinst2_status_latch_r", function(offset, data, mask)
  if offset == 0x60 or offset == 0x70 then
    latch_reads = latch_reads + 1
    log("Z80", offset == 0x60 and "latch_hi" or "latch_lo", offset, data, mask)
  end
end)

emu.wait(trace_seconds)

for chip = 0, 1 do
  local index = chip + 1
  log(
    "TRACE",
    "summary_oki" .. chip ..
      "_status_reads_" .. tostring(oki_status_reads[index]) ..
      "_starts_" .. tostring(oki_start_events[index]) ..
      "_stops_" .. tostring(oki_stop_events[index]) ..
      "_overlaps_" .. tostring(oki_overlap_events[index]) ..
      "_last_status_" .. hx(oki_last_status[index], 2) ..
      "_last_start_" .. hx(oki_last_start[index], 2) ..
      "_last_overlap_" .. hx(oki_last_overlap[index], 1),
    0,
    0,
    0)
end

log("TRACE", "summary_main_" .. hx(last_main_command, 4), 0, 0, 0)
log("TRACE", "summary_latch_reads_" .. tostring(latch_reads), 0, 0, 0)
log("TRACE", "summary_injected_" .. tostring(inject_index - 1), 0, 0, 0)
log("TRACE", "done", 0, 0, 0)
out:close()
machine:exit()
