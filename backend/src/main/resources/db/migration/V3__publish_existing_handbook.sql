-- Preserves the public documentation that existed before V2.
--
-- Before publication became a per-workspace setting, one workspace was served
-- publicly because a configuration property named it, and the shipped default for
-- that property was 'devforge-handbook'. V2 added published_at defaulting to NULL,
-- which silently took that already-public documentation offline on upgrade.
--
-- This republishes it, so an upgrade preserves behaviour instead of changing it.
-- Nothing is exposed that was not already readable by anyone.
--
-- Safe in every direction: a no-op on instances that never had a handbook, on
-- instances that renamed the slug (those admins publish from the UI), and on
-- workspaces an admin has since published or unpublished deliberately.
UPDATE workspaces
SET published_at = COALESCE(published_at, created_at)
WHERE slug = 'devforge-handbook'
  AND published_at IS NULL;
