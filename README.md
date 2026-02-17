Ovaj projekat predstavlja Java client–server chat aplikaciju koja kombinuje KryoNet (TCP socket) za komunikaciju u realnom vremenu i Java RMI za upravljanje chat sobama i pristup istoriji poruka.
Sistem jasno razdvaja real-time razmenu poruka od administrativnih i istorijskih operacija.

Socket sloj se koristi za slanje javnih poruka, poruka u sobama, privatnih (DM) i multicast poruka, kao i za propagaciju reply i edit događaja. 
RMI sloj omogućava kreiranje i listanje soba, priključenje sobi, dobijanje poslednjih poruka i učitavanje starijih poruka kroz paginaciju.

Nakon priključenja sobi, korisnik dobija poslednjih deset poruka, a sve nove poruke iz te sobe dobija u realnom vremenu samo ako je njen član. 
Sve poruke se čuvaju na serveru i imaju jedinstveni identifikator.
