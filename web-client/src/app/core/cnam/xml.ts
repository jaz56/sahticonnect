import { ActeCote, LettreCle } from './cnam.types';

/** Indente un document XML pour l'affichage (onglet « XML » de l'écran CNAM). */
export function formaterXml(xml: string): string {
  if (!xml) {
    return '';
  }
  let niveau = 0;
  return xml
    .replace(/>\s*</g, '><')
    .replace(/></g, '>\n<')
    .split('\n')
    .map((ligne) => {
      if (/^<\/[^>]+>$/.test(ligne)) {
        niveau = Math.max(niveau - 1, 0);
      }
      const indentee = '  '.repeat(niveau) + ligne;
      // ouvrante non auto-fermante, sans fermante sur la même ligne, et pas une déclaration <?xml ?>
      if (/^<[^/?!][^>]*[^/]>$/.test(ligne) || /^<[^/?!>]>$/.test(ligne)) {
        niveau++;
      }
      return indentee;
    })
    .join('\n');
}

/** Lit des actes saisis comme « CS 1 + K 10 » (ou séparés par des virgules). */
export function lireActes(saisie: string): ActeCote[] {
  return saisie
    .split(/[+,;]/)
    .map((morceau) => morceau.trim().split(/\s+/))
    .filter((parties) => parties.length >= 2 && parties[0])
    .map(([lettre, coefficient]) => ({
      lettreCle: lettre.toUpperCase() as LettreCle,
      coefficient: Number(coefficient.replace(',', '.')),
    }));
}
