import { Router } from 'express';
import { verificationRepo } from '../repo/verificationRepo.js';
import { tenantRepo } from '../repo/tenantRepo.js';
import { studentRepo } from '../repo/studentRepo.js';
import { QrService } from '../service/QrService.js';
import { DisplayLabels } from '../view/DisplayLabels.js';

export const verifyRoutes = Router();

const BASE_URL = process.env.CAMPUSOS_BASE_URL ?? 'http://localhost:8080';

/**
 * The QR target. NOT one of the 7 student views — it is the public page an employer lands on
 * after scanning a certificate. Deliberately unscoped: the unguessable verifyId is the
 * capability. Built from the configured origin, never from the request, so a spoofed Host
 * header cannot rewrite where the QR points.
 */
verifyRoutes.get('/:verifyId', (req, res) => {
  const { verifyId } = req.params;
  const v = verificationRepo.findByVerifyId(verifyId);
  if (!v) {
    res.status(404);
    return res.render('verify', { verifyId, v: null });
  }

  const tenant = tenantRepo.findById(v.tenantId);
  const holder = studentRepo.findByIdAndTenantId(v.studentId, v.tenantId)?.name ?? '—';
  const credential = DisplayLabels.credentialKind(v.kind);
  const issuedOn = new Date(v.issuedAt).toLocaleDateString('en-GB', { day: 'numeric', month: 'long', year: 'numeric' });
  const url = `${BASE_URL.replace(/\/+$/, '')}/verify/${verifyId}`;
  const publicDetail = DisplayLabels.publicDetail(v.detail);

  res.render('verify', {
    verifyId, v: { ...v, detail: publicDetail }, tenant, holder, credential, issuedOn,
    verifyUrl: url, qr: QrService.svg(url, 132),
  });
});
