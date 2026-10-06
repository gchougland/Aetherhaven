"""Attachment audit fixtures independent of the installed game."""
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
import zipfile

import audit_model_attachments as audit
import convert_cached_skins_to_models as skins


def node(name, piece=False, children=()):
    return {'name': name, 'shape': {'settings': {'isPiece': piece}}, 'children': list(children)}


class AttachmentAuditTest(unittest.TestCase):
    def setUp(self):
        self.temp = TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.native = self.root / 'Assets.zip'
        self.authored = self.root / 'authored'
        self.authored.mkdir()
        self.write_zip(self.native, {
            'Server/Models/Human.json': {'Model': 'Human.blockymodel'},
            'Common/Human.blockymodel': {'nodes': [node('Head')]},
            'Common/Ears.blockymodel': {'nodes': [node('Origin', children=[node('EarAccessory', True)])]},
            'Common/Plain.blockymodel': {'nodes': [node('Origin')]},
        })

    def tearDown(self):
        for archive in audit.ARCHIVES:
            archive.close()
        audit.ARCHIVES.clear()
        self.temp.cleanup()

    def write_zip(self, path, entries):
        with zipfile.ZipFile(path, 'w') as archive:
            for key, data in entries.items():
                archive.writestr(key, json.dumps(data))

    def test_packed_custom_model_nested_piece_is_checked(self):
        pack = self.root / 'addon.jar'
        self.write_zip(pack, {'Server/Aetherhaven/NpcModels/Visitor.json': {
            'Parent': 'Human', 'DefaultAttachments': [{'Model': 'Ears.blockymodel'}]}})
        audit.configure(self.native, [pack], self.authored)
        self.assertIn('Visitor', audit.AUTHORED)
        self.assertEqual([('Ears.blockymodel', ['EarAccessory'])], audit.audit('Visitor'))

    def test_plain_origin_and_later_mesh_override(self):
        pack = self.root / 'addon.zip'
        self.write_zip(pack, {
            'Server/Models/Visitor.json': {'Parent': 'Human', 'DefaultAttachments': [
                {'Model': 'Ears.blockymodel'}, {'Model': 'Plain.blockymodel'}]},
            'Common/Human.blockymodel': {'nodes': [node('Head', children=[node('EarAccessory')])]},
        })
        audit.configure(self.native, [pack], self.authored)
        self.assertEqual([], audit.audit('Visitor'))

    def test_missing_base_is_reported(self):
        folder = self.authored / 'Server/Models'
        folder.mkdir(parents=True)
        (folder / 'Broken.json').write_text(json.dumps({'Model': 'Missing.blockymodel'}))
        audit.configure(self.native, [], self.authored)
        self.assertEqual([('Missing.blockymodel', ['MISSING BASE MODEL FILE'])], audit.audit('Broken'))

    def test_exported_human_ears_resolve_earrings_and_preserve_tint(self):
        # Exercise the same exported attachment list that enters a content pack.
        # The native human ear mesh has no earring anchors; the resident mesh does.
        native_ears = skins.ModelAttachment(skins.EARS1_BLOCKYMODEL, 'Ear.png', 'Skin', '07')
        earrings = skins.ModelAttachment('Earrings.blockymodel', 'Metal.png')
        attachments = [native_ears, earrings]
        skins.sanitize_attachments(attachments)
        pack = self.root / 'exported.zip'
        self.write_zip(pack, {
            'Server/Models/Exported.json': {'Parent': 'Human', 'DefaultAttachments': [a.to_json() for a in attachments]},
            'Common/' + skins.RESIDENT_EARS_BLOCKYMODEL: {'nodes': [node('Head', True, [node('EarAccessory')])]},
            'Common/Earrings.blockymodel': {'nodes': [node('EarAccessory', True)]},
        })
        audit.configure(self.native, [pack], self.authored)
        self.assertEqual([], audit.audit('Exported'))
        self.assertEqual({'Model': skins.RESIDENT_EARS_BLOCKYMODEL, 'Texture': 'Ear.png',
                          'GradientSet': 'Skin', 'GradientId': '07'}, attachments[0].to_json())
        self.assertEqual(skins.EARS1_BLOCKYMODEL, native_ears.model)
        self.assertIs(earrings, attachments[1])
        skins.sanitize_attachments(attachments)
        self.assertEqual(2, len(attachments))

    def test_export_validation_uses_both_asset_roots_and_still_reports_missing_files(self):
        native_common = self.root / 'Common'
        resident_common = self.authored / 'Common'
        native_common.mkdir()
        model_path = resident_common / skins.RESIDENT_EARS_BLOCKYMODEL
        model_path.parent.mkdir(parents=True)
        model_path.write_text('{}')
        (native_common / 'Ears.png').write_bytes(b'fixture')
        model = {'DefaultAttachments': [skins.ModelAttachment(skins.RESIDENT_EARS_BLOCKYMODEL, 'Ears.png').to_json()]}
        self.assertEqual([], skins.validate_model(model, native_common, resident_common))
        model_path.unlink()
        self.assertEqual(1, len(skins.validate_model(model, native_common, resident_common)))


if __name__ == '__main__':
    unittest.main()
