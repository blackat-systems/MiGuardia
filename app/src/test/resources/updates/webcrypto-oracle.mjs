// Offline oracle: imports the actual read-only website code. No server, files, DB or network writes.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';
const root = process.argv[2];
assert.ok(root, 'MIGUARDIA_BETA_WEBSITE must identify the read-only website');
const module = name => import(pathToFileURL(resolve(root, name)).href);
const api = await module('lib/miguardia-beta/crypto.ts');
const validation = await module('lib/miguardia-beta/validation.ts');
const helpers = await module('tests/helpers.mjs');
const input = JSON.parse(readFileSync(0, 'utf8'));
const canonical = api.canonicalDownloadProof(input.fields);
assert.equal(input.proof, canonical);
assert.ok(canonical.endsWith('\n'));
assert.ok(await api.verifyInstallationSignature(input.public_spki, canonical, input.signature));
assert.equal(validation.parseChallengeRequest(input.challenge_request).releaseId, input.fields.releaseId);
assert.equal(validation.parseAuthorizationRequest(input.authorization_request).installationSignature, input.signature);
assert.throws(() => validation.parseChallengeRequest({...input.challenge_request, cookie: 'forbidden'}));
const webKey = await helpers.installationKey();
const webSignature = await webKey.sign(canonical);

const harness = await helpers.createHarness();
harness.objects.put(harness.release.r2Key, Uint8Array.from({length:12}, (_, index) => index));
const reservation = await harness.service.reserve(helpers.reservationInput());
const key = await helpers.installationKey();
const activation = await harness.service.activate({code: reservation.access_code, installationPublicKeySpki: key.spki});
const receipt = activation.activation_receipt;
const payload = api.parseActivationPayload(await harness.signer.verify(receipt));
assert.ok(payload);
// Release may advance while the original activation receipt and identity remain unchanged.
harness.store.setRelease(helpers.publishedRelease({id:'release_beta_2', versionCode:3, versionName:'2.0.0-beta.2'}));
const releaseId = 'release_beta_2';
const challenge = () => harness.service.createDownloadChallenge({activationReceipt:receipt, releaseId});
const request = async c => ({activationReceipt:receipt, releaseId, challengeId:c.challenge_id, challenge:c.challenge,
  installationSignature:await key.sign(api.canonicalDownloadProof({activationId:payload.activationId, releaseId, challengeId:c.challenge_id, challenge:c.challenge}))});
const denied = code => error => error.code === code;
const due = Date.parse(payload.feedbackDueAt);
harness.clock.set(due - 1); await challenge();
harness.clock.set(due); await challenge();
harness.clock.set(due + 1);
// A locally queued opinion has not called the remote service and therefore cannot authorize a download.
const localQueue = {category:'USABILITY', area:'CALENDAR', message:'Synthetic offline opinion.', consent:true};
await assert.rejects(challenge, denied('FEEDBACK_REQUIRED'));
const requestId = 'feedback_updates_28';
const feedbackProof = api.canonicalFeedbackProof({activationId:payload.activationId, requestId, bodySha256:await api.sha256(JSON.stringify(localQueue))});
await harness.service.submitFeedback({...localQueue, activationReceipt:receipt, requestId, installationSignature:await key.sign(feedbackProof)});
const first = await challenge();
const firstRequest = await request(first);
const authorization = await harness.service.authorizeDownload(firstRequest);
assert.equal((await harness.service.download({sessionToken:null, updateAuthorizationToken:authorization.authorization_token, rangeHeader:null})).release.id, releaseId);
await assert.rejects(() => harness.service.authorizeDownload(firstRequest), denied('DOWNLOAD_DENIED'));
// Lost authorization response is recovered only by a new explicit challenge, never replaying the spent one.
const next = await challenge(); assert.notEqual(first.challenge_id, next.challenge_id);
const nextRequest = await request(next);
// Valid JCA signature from a different installation must fail against this activation.
await assert.rejects(() => harness.service.authorizeDownload({...nextRequest, installationSignature:input.signature}), denied('DOWNLOAD_DENIED'));
harness.clock.advanceSeconds(301);
await assert.rejects(() => harness.service.authorizeDownload(nextRequest), denied('DOWNLOAD_DENIED'));
await assert.rejects(() => harness.service.download({sessionToken:null, updateAuthorizationToken:authorization.authorization_token, rangeHeader:null}), denied('DOWNLOAD_DENIED'));
// A token bound to a release cannot silently fetch a different publication.
const bound = await harness.service.authorizeDownload(await request(await challenge()));
harness.store.setRelease(helpers.publishedRelease({id:'release_beta_3', versionCode:4, versionName:'2.0.0-beta.3'}));
await assert.rejects(() => harness.service.download({sessionToken:null, updateAuthorizationToken:bound.authorization_token, rangeHeader:null}), denied('RELEASE_UNAVAILABLE'));
harness.store.setSlotState(1, 'REVOKED');
await assert.rejects(() => harness.service.createDownloadChallenge({activationReceipt:receipt, releaseId:'release_beta_3'}), denied('ACCESS_DENIED'));
const checks = ['canonical', 'jca-to-webcrypto', 'exact-request-fields', 'original-receipt-new-release', 'before-due', 'equal-due', 'queued-after-due-denied', 'received-after-due', 'consumed-challenge', 'fresh-after-lost-response', 'foreign-signature', 'expired-challenge', 'expired-authorization', 'changed-release', 'revoked'];
process.stdout.write(JSON.stringify({public_spki:webKey.spki, signature:webSignature, proof:canonical, checks:checks.length}));
