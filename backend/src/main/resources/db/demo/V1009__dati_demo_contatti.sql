-- Email inventate (dominio di esempio riservato, RFC 2606) per provare i promemoria: i casi fissi e metà dei clienti demo.
update persona set email = lower(cognome) || '.famiglia@example.org'
where id in ('00000000-0000-0000-0000-000000000501', '00000000-0000-0000-0000-000000000502', '00000000-0000-0000-0000-000000000503',
             '00000000-0000-0000-0000-000000000504', '00000000-0000-0000-0000-000000000505', '00000000-0000-0000-0000-000000000506',
             '00000000-0000-0000-0000-000000000507', '00000000-0000-0000-0000-000000000508', '00000000-0000-0000-0000-000000000510',
             '00000000-0000-0000-0000-000000000512');
update persona p set email = lower(translate(p.nome || '.' || p.cognome, 'àèéìòù ', 'aeeiou')) || '.' || substr(md5(p.id::text), 1, 4) || '@example.org'
where p.email is null and p.data_nascita is not null
  and not exists (select 1 from account a where a.persona_id = p.id)
  and abs(hashtext(p.id::text)) % 2 = 0;
