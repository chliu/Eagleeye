-- One-time migration: convert trade_date from INTEGER epoch-millis (Asia/Taipei
-- midnight) to ISO-8601 text "yyyy-MM-dd". Idempotent: only touches INTEGER rows.
-- The +8h offset recovers the true Taipei trading day before formatting.
-- Run once against ~/.eagleeye/data/eagleeye.db (back it up first).

BEGIN TRANSACTION;

UPDATE options_position
SET trade_date = date(trade_date/1000 + 8*3600, 'unixepoch')
WHERE typeof(trade_date) = 'integer';

UPDATE options_call_put_position
SET trade_date = date(trade_date/1000 + 8*3600, 'unixepoch')
WHERE typeof(trade_date) = 'integer';

UPDATE futures_position
SET trade_date = date(trade_date/1000 + 8*3600, 'unixepoch')
WHERE typeof(trade_date) = 'integer';

UPDATE futures_ah_position
SET trade_date = date(trade_date/1000 + 8*3600, 'unixepoch')
WHERE typeof(trade_date) = 'integer';

UPDATE institutional_flow
SET trade_date = date(trade_date/1000 + 8*3600, 'unixepoch')
WHERE typeof(trade_date) = 'integer';

UPDATE margin_transaction
SET trade_date = date(trade_date/1000 + 8*3600, 'unixepoch')
WHERE typeof(trade_date) = 'integer';

UPDATE taiex_index
SET trade_date = date(trade_date/1000 + 8*3600, 'unixepoch')
WHERE typeof(trade_date) = 'integer';

COMMIT;
