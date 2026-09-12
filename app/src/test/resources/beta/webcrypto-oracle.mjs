// Test-only oracle. Private keys are ephemeral and never leave this process.
// Regenerate public vectors: node webcrypto-oracle.mjs generate <read-only website root>
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';
import { readFileSync } from 'node:fs';
const b64 = bytes => Buffer.from(bytes).toString('base64url');
if (process.argv[2] === 'generate') {
  const module = name => import(pathToFileURL(resolve(process.argv[3], 'lib/miguardia-beta', name + '.ts')).href);
  const api = await module('crypto');
  const validation = await module('validation');
  const signer = await api.ReceiptSigner.ephemeralForTests('webcrypto-vector');
  const publicKey = await crypto.subtle.importKey('jwk', await signer.exportPublicJwk(), {name:'ECDSA',namedCurve:'P-256'}, true, ['verify']);
  const payload = api.canonicalActivationPayload({kid:signer.kid, activationId:'vector_activation', activatedAt:'2026-09-03T12:00:00.000Z',
    feedbackDueAt:'2026-09-18T12:00:00.000Z', applicationId:'com.blackatsystems.miguardia', releaseId:'vector_release',
    releaseVersionCode:1, releaseCertificateSha256:'b'.repeat(64), installationPublicKeySha256:'a'.repeat(64)});
  const activation = await signer.sign(payload);
  const inputs = ['\uFEFF\u00a0a\u0301\u3000', 'Emoji 😀', '"\\/<>&', 'a\r\n\tb', '😀'.repeat(1000), '\ud800', 'a\u2028b', '\u1680\u2000Texto\u202f'];
  console.log(JSON.stringify({public_spki:b64(await crypto.subtle.exportKey('spki',publicKey)), activation}));
  for (const input of inputs) {
    const normalized = validation.parseFeedback({activation_receipt:activation, request_id:'vector_request', category:'IDEA', area:'CALENDAR',
      message:input, consent:true, installation_signature:'A'.repeat(86)});
    const body = JSON.stringify({category:normalized.category, area:normalized.area, message:normalized.message, consent:normalized.consent});
    console.log(JSON.stringify({input, normalized:normalized.message, body, hash:await api.sha256(body)}));
  }
} else {
  const input = JSON.parse(readFileSync(0,'utf8'));
  const key = await crypto.subtle.importKey('spki', Buffer.from(input.public_spki,'base64url'), {name:'ECDSA',namedCurve:'P-256'}, false, ['verify']);
  if (!await crypto.subtle.verify({name:'ECDSA',hash:'SHA-256'}, key, Buffer.from(input.signature,'base64url'), Buffer.from(input.payload,'utf8'))) process.exit(1);
  if (process.argv[2]) {
    const api = await import(pathToFileURL(resolve(process.argv[2], 'lib/miguardia-beta/crypto.ts')).href);
    if (!await api.verifyInstallationSignature(input.public_spki,input.payload,input.signature)) process.exit(2);
  }
  console.log('verified');
}
