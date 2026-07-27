-- Keep the detail of a partly-applied sync, not just its count.
--
-- The outcome already recorded "3 created, 1 problem(s)", but the problems
-- themselves lived only in the response to a manual sync. So a reload lost them,
-- and a webhook-triggered sync never showed them at all — which makes reporting a
-- problem pointless, since the operator cannot act on a number.
--
-- Newline separated rather than a table: they belong to the last attempt only, and
-- are replaced wholesale by the next one.
ALTER TABLE sync_configurations
    ADD COLUMN last_problems TEXT;
