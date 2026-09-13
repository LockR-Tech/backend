import bcrypt
h1 = b'$2a$10$iYGwq/z2LBYxTd92vFkTieFp8ZUrE.PHapoOqFETmA6tAAZCOXyfS'
h2 = b'$2a$10$S2LjQzym5drWNMKOdCpbWuZIKwJONRrabq54MiQnweVSvRlHMvf5O'
print("MS hash vs secret123:", bcrypt.checkpw(b'secret123', h1))
print("MS hash vs 12345678:", bcrypt.checkpw(b'12345678', h1))
print("Main hash vs 12345678:", bcrypt.checkpw(b'12345678', h2))
