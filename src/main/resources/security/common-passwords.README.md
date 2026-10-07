# Lista de contraseñas comunes

`common-passwords.txt` tiene las 3000 contraseñas más frecuentes de filtraciones públicas que tienen entre 12 y 64 caracteres. La
política de contraseñas del registro rechaza cualquiera de ellas aunque cumpla la longitud (OWASP ASVS 6.2.4). La comparación ignora
mayúsculas, los espacios de los extremos y la forma Unicode.

`cameia-web` toma este mismo archivo, para que cliente y servidor rechacen las mismas contraseñas.

## Formato

- UTF-8 sin marca de orden de bytes, fin de línea `\n`, una contraseña por línea.
- Cada entrada está en minúsculas, sin espacios en los extremos, en forma NFC y con 12 a 64 puntos de código. No hay líneas vacías ni
  entradas repetidas.
- El servicio no arranca si el archivo falta o no cumple alguna de estas reglas, o si tiene menos de 3000 entradas.

## Procedencia

| Dato | Valor |
|---|---|
| Fuente | SecLists, `Passwords/Common-Credentials/Pwdb_top-1000000.txt` (un millón de contraseñas ordenadas por frecuencia) |
| URL | `https://raw.githubusercontent.com/danielmiessler/SecLists/12274c98fdebe98c7a7284914436a472ed469aed/Passwords/Common-Credentials/Pwdb_top-1000000.txt` |
| Licencia | MIT, Copyright (c) 2018 Daniel Miessler |
| Fecha de descarga | 7 de octubre de 2026 |
| SHA-256 de la fuente | `e9a88f67aafe65496682dc374559ee714e978bee50314767494c3e37a18c9fc8` |
| SHA-256 de `common-passwords.txt` | `7baf9176eb59551947ef7d4d47654da4e17d02251dc905292e10844ff23c1031` |

## Cómo se generó

Se recorre la fuente en su orden (de la más frecuente a la menos), y de cada línea se toma el texto recortado, en minúsculas y en NFC.
Se descarta si tiene menos de 12 o más de 64 puntos de código (más cortas ya las rechaza la longitud mínima y más largas, la máxima) o si
contiene restos de codificación de la fuente (el carácter de reemplazo U+FFFD, o su lectura errónea como Windows-1251, «пїѕ»: no son
contraseñas que alguien escriba). Se conservan las primeras 3000 sin repetir. Script de PowerShell usado:

```powershell
param([string]$Fuente, [string]$Destino, [int]$Cantidad = 3000)
$vistas = New-Object 'System.Collections.Generic.HashSet[string]'
$lista = New-Object System.Collections.Generic.List[string]
foreach ($linea in [IO.File]::ReadLines($Fuente, [Text.Encoding]::UTF8)) {
    $texto = $linea.Trim().ToLowerInvariant().Normalize([Text.NormalizationForm]::FormC)
    $puntos = 0
    for ($i = 0; $i -lt $texto.Length; $i++) { if (-not [char]::IsLowSurrogate($texto[$i])) { $puntos++ } }
    if ($puntos -lt 12 -or $puntos -gt 64) { continue }
    if ($texto.Contains([string][char]0x043F + [char]0x0457 + [char]0x0455) -or $texto.Contains([string][char]0xFFFD)) { continue }
    if ($vistas.Add($texto)) { $lista.Add($texto) }
    if ($lista.Count -eq $Cantidad) { break }
}
[IO.File]::WriteAllText($Destino, (($lista -join "`n") + "`n"), (New-Object Text.UTF8Encoding $false))
```

Para regenerarla, se descarga la fuente de la URL anterior en una carpeta vacía, se comprueba su SHA-256 y se ejecuta el script con
`-Fuente` apuntando a ella y `-Destino` a este archivo. El resultado debe tener el mismo SHA-256.
