-- Inworld's deliveryMode enum is DELIVERY_MODE_UNSPECIFIED | STABLE | BALANCED | CREATIVE.
-- EXPRESSIVE was never a member, so podcasts configured with it were sending an invalid value.
-- CREATIVE is the end of the scale EXPRESSIVE was reaching for, so this is a rename in place.
-- tts_settings is a JSON text column; both the compact and the spaced serialisations are covered.
UPDATE podcasts
SET tts_settings = REPLACE(
        REPLACE(tts_settings, '"deliveryMode":"EXPRESSIVE"', '"deliveryMode":"CREATIVE"'),
        '"deliveryMode": "EXPRESSIVE"', '"deliveryMode": "CREATIVE"'
    )
WHERE tts_settings LIKE '%"deliveryMode"%EXPRESSIVE%';
